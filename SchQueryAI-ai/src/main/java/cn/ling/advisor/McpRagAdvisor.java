package cn.ling.advisor;

import cn.ling.context.ChatContext;
import cn.ling.domain.pojo.ChatPreset;
import cn.ling.service.SysConfigService;
import cn.ling.service.mcp.McpSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

/**
 * MCP 网络检索增强
 * - 从预设中读取 MCP 配置（ChatPreset.mcpMode, ChatPreset.mcpServers）
 * - mcpMode=off：关闭
 * - mcpMode=fallback：知识库无命中再走MCP
 * - mcpMode=merge：知识库 + MCP 合并
 * - 负责统一融合 用户提示词、知识库内容、MCP内容
 */
@Slf4j
@Component
public class McpRagAdvisor implements BaseAdvisor {

    private static final String KEY_MCP_MODE = "chat.default.mcpMode";
    private static final String KEY_MERGE_MODE = "chat.default.promptMergeMode";
    private static final String KEY_WEIGHT_USER = "chat.weight.user";
    private static final String KEY_WEIGHT_KNOWLEDGE = "chat.weight.knowledge";
    private static final String KEY_WEIGHT_MCP = "chat.weight.mcp";

    private final SysConfigService sysConfigService;
    private final McpSearchService mcpSearchService;

    public McpRagAdvisor(SysConfigService sysConfigService, McpSearchService mcpSearchService) {
        this.sysConfigService = sysConfigService;
        this.mcpSearchService = mcpSearchService;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        // 从 ChatContext 获取预设配置
        ChatPreset preset = ChatContext.getPreset();
        McpMode mcpMode = resolveMcpMode(preset);

        String userInput = chatClientRequest.prompt().getUserMessage().getText();
        if (!StringUtils.hasText(userInput)) {
            return chatClientRequest;
        }

        // 从 context 获取知识库已经准备的内容
        String userSystemMessage = (String) chatClientRequest.context().get("userSystemMessage");
        String intentSystemPrompt = (String) chatClientRequest.context().get("intentSystemPrompt");
        String knowledgePrompt = (String) chatClientRequest.context().get("knowledgePrompt");
        String knowledgeContent = (String) chatClientRequest.context().get("knowledgeContent");
        boolean knowledgeHit = Boolean.TRUE.equals(chatClientRequest.context().get("knowledgeHit"));

        if (StringUtils.hasText(intentSystemPrompt)) {
            userSystemMessage = mergeIntentPrompt(userSystemMessage, intentSystemPrompt);
        }

        // 决定是否使用 MCP
        // 注意：mcpMode=off 只关闭 MCP 检索；提示词融合（用户/知识库）仍然执行
        chatClientRequest.context().put("mcpHit", false);
        String mcpContent = null;
        boolean shouldUseMcp = mcpMode != McpMode.OFF
                && (mcpMode == McpMode.MERGE || (mcpMode == McpMode.FALLBACK && !knowledgeHit));
        if (shouldUseMcp) {
            mcpContent = mcpSearchService.tavilySearch(userInput);
            if (StringUtils.hasText(mcpContent)) {
                chatClientRequest.context().put("mcpHit", true);
                mcpContent = buildMcpContentSection(mcpContent.trim());
            }
        }

        // ✨ 核心：使用 PromptMergeStrategy 统一融合所有提示词
        PromptMergeStrategy.MergeMode mergeMode = resolveMergeMode(preset);
        PromptMergeStrategy.MergeConfig mergeConfig = PromptMergeStrategy.MergeConfig.builder()
                .mode(mergeMode)
                .userPromptWeight(resolveWeight(preset, "user", 0.6))
                .knowledgePromptWeight(resolveWeight(preset, "knowledge", 0.25))
                .mcpPromptWeight(resolveWeight(preset, "mcp", 0.15))
                .maxTotalLength(8000)
                .preserveUserPrompt(true)
                .addSeparators(true)
                .build();

        String knowledgeForSystem = knowledgeContent;
        if (StringUtils.hasText(knowledgeContent)) {
            if (shouldSuppressKnowledge(userInput)) {
                log.debug("跳过知识库内容拼接：检测到短问候/非检索型输入");
                knowledgeForSystem = null;
            } else {
                String knowledgeBlock = "\n\n【知识库资料】\n" + knowledgeContent.trim();
                chatClientRequest = chatClientRequest.mutate()
                        .prompt(chatClientRequest.prompt().augmentUserMessage(knowledgeBlock))
                        .build();
                knowledgeForSystem = null;
            }
        }

        PromptMergeStrategy.MergeResult result = PromptMergeStrategy.merge(
                userSystemMessage,
                knowledgePrompt,
                knowledgeForSystem,
                mcpContent,
                mergeConfig
        );

        log.debug("提示词融合完成:\n{}", result.getDebugInfo());

        // 如果有融合后的内容，注入到 system message
        if (StringUtils.hasText(result.getFinalSystemPrompt())) {
            return chatClientRequest.mutate()
                    .prompt(chatClientRequest.prompt().augmentSystemMessage(result.getFinalSystemPrompt()))
                    .build();
        }

        return chatClientRequest;
    }

    /**
     * 解析 MCP 模式
     */
    private McpMode resolveMcpMode(ChatPreset preset) {
        String raw = null;

        // 优先使用预设中的 mcpMode
        if (preset != null && StringUtils.hasText(preset.getMcpMode())) {
            raw = preset.getMcpMode();
            log.debug("使用预设的 MCP 模式: {}", raw);
        } else {
            // 如果预设中没有配置，则使用全局默认值
            raw = sysConfigService.getConfigValue(KEY_MCP_MODE);
            log.debug("使用全局默认 MCP 模式: {}", raw);
        }

        if (!StringUtils.hasText(raw)) {
            return McpMode.FALLBACK;
        }
        String normalized = raw.trim().toLowerCase();
        return switch (normalized) {
            case "off", "false", "0", "disable", "disabled" -> McpMode.OFF;
            case "merge", "both", "all" -> McpMode.MERGE;
            case "fallback", "kb_then_mcp", "knowledge_then_mcp" -> McpMode.FALLBACK;
            default -> McpMode.FALLBACK;
        };
    }

    /**
     * 解析融合模式
     */
    private PromptMergeStrategy.MergeMode resolveMergeMode(ChatPreset preset) {
        PromptMergeStrategy.MergeMode fromPreset = parseMergeMode(preset != null ? preset.getPromptMergeMode() : null);
        if (fromPreset != null) {
            log.debug("使用预设的提示词融合模式: {}", preset.getPromptMergeMode());
            return fromPreset;
        }

        String globalRaw = sysConfigService.getConfigValue(KEY_MERGE_MODE);
        PromptMergeStrategy.MergeMode fromGlobal = parseMergeMode(globalRaw);
        if (fromGlobal != null) {
            log.debug("使用全局默认提示词融合模式: {}", globalRaw);
            return fromGlobal;
        }

        return PromptMergeStrategy.MergeMode.USER_FIRST;
    }

    /**
     * 解析权重配置
     */
    private Double resolveWeight(ChatPreset preset, String source, double defaultWeight) {
        PromptMergeStrategy.MergeMode presetMode = parseMergeMode(preset != null ? preset.getPromptMergeMode() : null);
        Double fromPreset = presetMode == PromptMergeStrategy.MergeMode.BALANCED
                ? normalizeNullableWeight(resolvePresetWeight(preset, source))
                : null;
        if (fromPreset != null) {
            return fromPreset;
        }

        String key = resolveWeightKey(source);
        if (key == null) {
            return defaultWeight;
        }
        Double fromGlobal = parseAndNormalizeNullableWeight(sysConfigService.getConfigValue(key));
        return fromGlobal != null ? fromGlobal : defaultWeight;
    }

    private static PromptMergeStrategy.MergeMode parseMergeMode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.trim().toLowerCase();
        return switch (normalized) {
            case "user_first", "user", "userfirst" -> PromptMergeStrategy.MergeMode.USER_FIRST;
            case "knowledge_first", "knowledge", "knowledgefirst" -> PromptMergeStrategy.MergeMode.KNOWLEDGE_FIRST;
            case "balanced", "balance" -> PromptMergeStrategy.MergeMode.BALANCED;
            case "layered", "layer" -> PromptMergeStrategy.MergeMode.LAYERED;
            default -> null;
        };
    }

    private static Double resolvePresetWeight(ChatPreset preset, String source) {
        if (preset == null || !StringUtils.hasText(source)) {
            return null;
        }
        return switch (source.trim().toLowerCase()) {
            case "user" -> preset.getWeightUser();
            case "knowledge" -> preset.getWeightKnowledge();
            case "mcp" -> preset.getWeightMcp();
            default -> null;
        };
    }

    private static String resolveWeightKey(String source) {
        if (!StringUtils.hasText(source)) {
            return null;
        }
        return switch (source.trim().toLowerCase()) {
            case "user" -> KEY_WEIGHT_USER;
            case "knowledge" -> KEY_WEIGHT_KNOWLEDGE;
            case "mcp" -> KEY_WEIGHT_MCP;
            default -> null;
        };
    }

    private static Double parseAndNormalizeNullableWeight(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return normalizeNullableWeight(Double.parseDouble(raw.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double normalizeNullableWeight(Double raw) {
        if (raw == null || !Double.isFinite(raw)) {
            return null;
        }
        if (raw < 0) {
            return 0.0;
        }
        if (raw > 1) {
            return 1.0;
        }
        return raw;
    }

    private static boolean shouldSuppressKnowledge(String userInput) {
        if (!StringUtils.hasText(userInput)) {
            return true;
        }
        String normalized = userInput.trim().toLowerCase();
        if (normalized.length() <= 2) {
            return true;
        }
        String compact = normalized.replaceAll("\\s+", "");
        return compact.matches("^(你好|您好|嗨|哈喽|哈囉|在吗|在么|早上好|上午好|中午好|下午好|晚上好|晚安|hi|hello|hey)$");
    }

    /**
     * 构建 MCP 内容区块
     */
    private static String buildMcpContentSection(String text) {
        return "以下内容来自网络检索结果，仅用于辅助回答，请自行判断其准确性与时效性：\n\n" + text;
    }

    private static String mergeIntentPrompt(String userSystemMessage, String intentSystemPrompt) {
        if (!StringUtils.hasText(userSystemMessage)) {
            return intentSystemPrompt.trim();
        }
        if (!StringUtils.hasText(intentSystemPrompt)) {
            return userSystemMessage.trim();
        }
        return userSystemMessage.trim() + "\n\n## 意图导向补充\n" + intentSystemPrompt.trim();
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
        return 3;
    }

    private enum McpMode {
        OFF,
        FALLBACK,
        MERGE
    }
}
