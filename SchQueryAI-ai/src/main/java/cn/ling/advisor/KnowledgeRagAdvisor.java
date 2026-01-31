package cn.ling.advisor;

import cn.ling.context.ChatContext;
import cn.ling.domain.pojo.ChatPreset;
import cn.ling.domain.pojo.KnowledgeInfo;
import cn.ling.embedding.EmbeddingModelContext;
import cn.ling.service.KnowledgeInfoService;
import cn.ling.rpc.RerankRpc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库 RAG 顾问（单 collection + knowledge_id 过滤）
 * - 从预设中读取知识库ID（ChatPreset.kid），并按 knowledge_id 过滤检索
 * - 将检索片段注入到 system message，供模型回答时引用
 * - 使用 PromptMergeStrategy 合理融合用户提示词和知识库内容
 */
@Slf4j
@Component
public class KnowledgeRagAdvisor implements BaseAdvisor {

    /**
     * Embedding 查询最大长度（字符）。
     * 过长的用户输入可能导致 embeddings 接口超时/断连（尤其是长 Markdown 请求）。
     */
    private static final int MAX_EMBEDDING_QUERY_CHARS = 800;

    private final VectorStore qdrantVectorStore;
    private final SearchRequest baseSearchRequest;
    private final KnowledgeInfoService knowledgeInfoService;
    private final RerankRpc rerankRpc;

    public KnowledgeRagAdvisor(
            @Qualifier("qdrantVectorStore") VectorStore qdrantVectorStore,
            @Qualifier("searchRequest") SearchRequest baseSearchRequest,
            KnowledgeInfoService knowledgeInfoService,
            RerankRpc rerankRpc
    ) {
        this.qdrantVectorStore = qdrantVectorStore;
        this.baseSearchRequest = baseSearchRequest;
        this.knowledgeInfoService = knowledgeInfoService;
        this.rerankRpc = rerankRpc;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        String userInput = chatClientRequest.prompt().getUserMessage().getText();
        if (!StringUtils.hasText(userInput)) {
            return chatClientRequest;
        }

        // 从 ChatContext 获取预设配置
        ChatPreset preset = ChatContext.getPreset();

        // 无论知识库是否命中，都要把用户系统提示词写入 context，避免后续顾问丢失
        // 注意：Spring AI 会对 context 执行 Map.copyOf，context 不允许包含 null value
        String userSystemMessage = (preset != null && StringUtils.hasText(preset.getSystemMessage()))
                ? preset.getSystemMessage().trim()
                : null;
        if (StringUtils.hasText(userSystemMessage)) {
            chatClientRequest.context().put("userSystemMessage", userSystemMessage);
        } else {
            chatClientRequest.context().remove("userSystemMessage");
        }

        Long knowledgeId = null;

        // 优先使用预设中的 kid
        if (preset != null && StringUtils.hasText(preset.getKid())) {
            try {
                knowledgeId = Long.parseLong(preset.getKid().trim());
                log.debug("使用预设的知识库ID: {}", knowledgeId);
            } catch (NumberFormatException e) {
                log.debug("预设的 kid 格式无效: {}", preset.getKid());
            }
        }

        // 如果预设中没有配置，则使用默认知识库
        if (knowledgeId == null) {
            try {
                knowledgeId = knowledgeInfoService.ensureDefaultKnowledgeId();
                log.debug("使用默认知识库ID: {}", knowledgeId);
            } catch (Exception e) {
                log.debug("读取默认知识库失败，将跳过RAG: {}", e.getMessage());
            }
        }

        if (knowledgeId == null) {
            chatClientRequest.context().put("knowledgeHit", false);
            chatClientRequest.context().remove("knowledgePrompt");
            chatClientRequest.context().remove("knowledgeContent");
            return chatClientRequest;
        }

        chatClientRequest.context().put("knowledgeId", knowledgeId);

        KnowledgeInfo knowledgeInfo = null;
        try {
            knowledgeInfo = knowledgeInfoService.getById(knowledgeId);
        } catch (Exception e) {
            log.debug("获取知识库信息失败: {}", e.getMessage());
        }
        if (knowledgeInfo != null) {
            log.info("知识库检索配置: knowledgeId={}, use_rerank={}, min_score={}, rerank_model={}",
                    knowledgeId,
                    knowledgeInfo.getUseRerank(),
                    knowledgeInfo.getMinScore(),
                    knowledgeInfo.getRerankModelName());
        } else {
            log.info("知识库检索配置: knowledgeId={} (未找到对应知识库配置)", knowledgeId);
        }

        try {
            int topK = resolveTopK(knowledgeInfo, baseSearchRequest.getTopK());
            int candidateCount = resolveCandidateCount(knowledgeInfo, topK);
            String embeddingModelName = (knowledgeInfo != null && StringUtils.hasText(knowledgeInfo.getEmbeddingModelName()))
                    ? knowledgeInfo.getEmbeddingModelName().trim()
                    : null;

            String queryForEmbedding = normalizeQueryForEmbedding(userInput);
            SearchRequest request = SearchRequest.from(baseSearchRequest)
                    .query(queryForEmbedding)
                    .topK(candidateCount)
                    .filterExpression("WHERE knowledge_id == " + knowledgeId)
                    .build();

            List<Document> docs = EmbeddingModelContext.withModel(embeddingModelName, () -> qdrantVectorStore.similaritySearch(request));
            if (docs == null || docs.isEmpty()) {
                // 兼容：部分数据将 knowledge_id 写为字符串（如 metadata 中 Long 转换为 String）
                SearchRequest stringFilter = SearchRequest.from(baseSearchRequest)
                        .query(queryForEmbedding)
                        .topK(candidateCount)
                        .filterExpression("WHERE knowledge_id == '" + knowledgeId + "'")
                        .build();
                docs = EmbeddingModelContext.withModel(embeddingModelName, () -> qdrantVectorStore.similaritySearch(stringFilter));
            }

            if (docs == null || docs.isEmpty()) {
                // 兼容历史数据：旧数据未写入 knowledge_id 元数据时，先做一次无过滤检索兜底
                SearchRequest fallback = SearchRequest.from(baseSearchRequest)
                        .query(queryForEmbedding)
                        .topK(topK)
                        .build();
                docs = EmbeddingModelContext.withModel(embeddingModelName, () -> qdrantVectorStore.similaritySearch(fallback));
            }

            docs = applyRerankIfEnabled(docs, knowledgeInfo, userInput, topK);

            String knowledgeContent = buildKnowledgeContext(docs);
            if (!StringUtils.hasText(knowledgeContent)) {
                log.info("⚠️ 知识库检索结果为空或全部被 rerank 过滤，本次对话将不使用知识库内容");
                chatClientRequest.context().put("knowledgeHit", false);
                chatClientRequest.context().remove("knowledgePrompt");
                chatClientRequest.context().remove("knowledgeContent");
                return chatClientRequest;
            }

            log.info("✅ 知识库检索成功，获得 {} 个有效片段，将注入到上下文中", 
                docs.stream().filter(d -> StringUtils.hasText(d.getText())).count());
            chatClientRequest.context().put("knowledgeHit", true);
            chatClientRequest.context().put("knowledgeId", knowledgeId);

            // ✨ 新增：保存知识库内容到 context，供 McpRagAdvisor 使用
            String knowledgePrompt = getKnowledgePrompt(knowledgeInfo);
            chatClientRequest.context().put("knowledgePrompt", knowledgePrompt);
            chatClientRequest.context().put("knowledgeContent", knowledgeContent);

            // userSystemMessage 已在前面写入 context
            // 暂时不在这里注入，等待 McpRagAdvisor 执行后统一融合
            return chatClientRequest;

        } catch (Exception e) {
            log.warn("知识库RAG检索失败，将跳过本次检索: {}", e.getMessage());
            chatClientRequest.context().put("knowledgeHit", false);
            chatClientRequest.context().remove("knowledgePrompt");
            chatClientRequest.context().remove("knowledgeContent");
            return chatClientRequest;
        }
    }

    private static String buildKnowledgeContext(List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return "";
        }
        List<String> lines = docs.stream()
                .map(d -> StringUtils.hasText(d.getText()) ? d.getText().trim() : "")
                .filter(StringUtils::hasText)
                .limit(10)
                .collect(Collectors.toList());
        if (lines.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append("【片段").append(i + 1).append("】").append(lines.get(i)).append("\n");
        }
        return sb.toString().trim();
    }

    private static String getKnowledgePrompt(KnowledgeInfo knowledgeInfo) {
        if (knowledgeInfo == null || !StringUtils.hasText(knowledgeInfo.getSystemPrompt())) {
            return "【重要提示】以下内容仅作为检索资料参考，不代表你的身份或立场。\n" +
                   "严格要求：\n" +
                   "1. 禁止以资料中任何人物的身份自称或代入回答\n" +
                   "2. 如资料内容与用户问题无关，请基于通用知识回答，勿强行使用无关资料\n" +
                   "3. 若资料确实相关，可客观引用并说明来源";
        }
        return knowledgeInfo.getSystemPrompt().trim();
    }

    private static String normalizeQueryForEmbedding(String userInput) {
        if (!StringUtils.hasText(userInput)) {
            return "";
        }
        String normalized = userInput.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_EMBEDDING_QUERY_CHARS) {
            return normalized;
        }
        return normalized.substring(0, MAX_EMBEDDING_QUERY_CHARS);
    }

    private static int resolveTopK(KnowledgeInfo knowledgeInfo, int defaultTopK) {
        int topK = defaultTopK;
        if (knowledgeInfo != null && knowledgeInfo.getRetrieveLimit() != null && knowledgeInfo.getRetrieveLimit() > 0) {
            topK = knowledgeInfo.getRetrieveLimit();
        }
        if (topK <= 0) {
            topK = 6;
        }
        if (topK > 20) {
            topK = 20;
        }
        return topK;
    }

    private static int resolveCandidateCount(KnowledgeInfo knowledgeInfo, int topK) {
        int candidate = topK;
        if (knowledgeInfo != null && knowledgeInfo.getCandidateCount() != null && knowledgeInfo.getCandidateCount() > 0) {
            candidate = knowledgeInfo.getCandidateCount();
        }
        if (candidate < topK) {
            candidate = topK;
        }
        if (candidate > 50) {
            candidate = 50;
        }
        return candidate;
    }

    private List<Document> applyRerankIfEnabled(List<Document> docs, KnowledgeInfo knowledgeInfo, String query, int topK) {
        if (docs == null || docs.isEmpty() || knowledgeInfo == null) {
            return docs;
        }
        if (knowledgeInfo.getUseRerank() == null || knowledgeInfo.getUseRerank() != 1) {
            return docs;
        }
        if (!StringUtils.hasText(query)) {
            return docs;
        }

        try {
            List<Document> candidates = docs.stream()
                    .filter(d -> d != null && StringUtils.hasText(d.getText()))
                    .collect(Collectors.toList());
            if (candidates.isEmpty()) {
                return docs;
            }
            String[] candidateTexts = candidates.stream()
                    .map(Document::getText)
                    .map(String::trim)
                    .toArray(String[]::new);

            RerankRpc.RerankRequest req = new RerankRpc.RerankRequest();
            if (StringUtils.hasText(knowledgeInfo.getRerankModelName())) {
                req.setModel(knowledgeInfo.getRerankModelName().trim());
            }
            req.setInput(new RerankRpc.RerankRequest.Input(query.trim(), candidateTexts));
            req.setParameters(new RerankRpc.RerankRequest.Parameters(true, candidateTexts.length, null));

            RerankRpc.RerankResponse resp = rerankRpc.rerank(req);
            if (resp == null || resp.getOutput() == null || resp.getOutput().getResults() == null) {
                return docs;
            }

            Double minScore = knowledgeInfo.getMinScore();
            
            // 调试日志：输出所有 rerank 结果的分数
            log.info("Rerank 结果详情:");
            for (var result : resp.getOutput().getResults()) {
                if (result != null && result.getIndex() != null && result.getIndex() < candidates.size()) {
                    String preview = candidates.get(result.getIndex()).getText();
                    if (preview.length() > 50) {
                        preview = preview.substring(0, 50) + "...";
                    }
                    log.info("  [片段{}] 分数={}, 内容={}", 
                        result.getIndex() + 1, 
                        result.getRelevance_score(), 
                        preview);
                }
            }
            log.info("当前分数阈值: minScore={}", minScore);
            
            List<Document> reranked = Arrays.stream(resp.getOutput().getResults())
                    .filter(r -> r != null && r.getIndex() != null && r.getIndex() >= 0 && r.getIndex() < candidates.size())
                    .filter(r -> {
                        boolean pass = minScore == null || (r.getRelevance_score() != null && r.getRelevance_score() >= minScore);
                        if (!pass && r.getRelevance_score() != null) {
                            log.debug(" 片段{} 被过滤: 分数={} < 阈值={}", r.getIndex() + 1, r.getRelevance_score(), minScore);
                        }
                        return pass;
                    })
                    .map(r -> candidates.get(r.getIndex()))
                    .filter(d -> d != null && StringUtils.hasText(d.getText()))
                    .limit(topK)
                    .collect(Collectors.toList());

            log.info("Rerank 过滤后保留 {} 个片段（原始{}个，topK={}）", reranked.size(), candidates.size(), topK);
            
            //  重要：如果 rerank 过滤后结果为空，应该返回空列表而非兜底到原始结果
            // 这样才能让后续的 knowledgeContent 判断生效，避免强行使用不相关内容
            return reranked;
        } catch (Exception e) {
            log.debug("rerank执行失败，将使用向量检索结果: {}", e.getMessage());
            return docs;
        }
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        ChatClientRequest processedRequest = before(chatClientRequest, callAdvisorChain);
        return callAdvisorChain.nextCall(processedRequest);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        ChatClientRequest processedRequest = before(chatClientRequest, streamAdvisorChain);
        return streamAdvisorChain.nextStream(processedRequest);
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return 2;
    }
}
