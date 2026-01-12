package cn.ling.controller;

import cn.ling.Result;
import cn.ling.embedding.EmbeddingModelContext;
import cn.ling.domain.pojo.DocumentChunks;
import cn.ling.domain.pojo.Documents;
import cn.ling.service.DocumentChunksService;
import cn.ling.service.DocumentsService;
import cn.ling.service.KnowledgeInfoService;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识库片段管理（基于 document_chunks）
 */
@RestController
@RequestMapping("/knowledge/fragment")
public class KnowledgeFragmentController {

    @Resource
    private DocumentChunksService documentChunksService;

    @Resource
    private KnowledgeInfoService knowledgeInfoService;

    @Resource
    private DocumentsService documentsService;

    @Resource(name = "qdrantVectorStore")
    private VectorStore qdrantVectorStore;

    @GetMapping("/list/{docId}")
    public Result<List<DocumentChunks>> list(@PathVariable("docId") Long docId) {
        if (docId == null) {
            return Result.error(400, "docId不能为空");
        }
        List<DocumentChunks> list = documentChunksService.lambdaQuery()
                .eq(DocumentChunks::getDocumentId, docId)
                .orderByAsc(DocumentChunks::getChunkIndex)
                .list();
        return Result.success(list);
    }

    @PostMapping("/updateContent")
    public Result<String> updateContent(@RequestBody FragmentUpdateRequest req) {
        if (req == null || req.getId() == null) {
            return Result.error(400, "id不能为空");
        }
        if (!StringUtils.hasText(req.getContent())) {
            return Result.error(400, "content不能为空");
        }

        DocumentChunks chunk = documentChunksService.getById(req.getId());
        if (chunk == null) {
            return Result.error(404, "未找到片段");
        }
        if (!StringUtils.hasText(chunk.getQdrantPointId())) {
            return Result.error("片段未绑定qdrant_point_id，无法更新向量");
        }

        try {
            // 1) 更新向量：删除旧向量并用相同 pointId 重建
            qdrantVectorStore.delete(List.of(chunk.getQdrantPointId()));
            Map<String, Object> metadata = chunk.getMetadata() == null ? new HashMap<>() : new HashMap<>(chunk.getMetadata());
            if (chunk.getKnowledgeId() != null) {
                metadata.putIfAbsent("knowledge_id", chunk.getKnowledgeId());
            }
            if (chunk.getDocumentId() != null) {
                metadata.putIfAbsent("document_id", chunk.getDocumentId());
            }
            if (chunk.getChunkIndex() != null) {
                metadata.putIfAbsent("chunk_index", chunk.getChunkIndex());
            }
            chunk.setMetadata(metadata);
            Map<String, Object> qdrantMetadata = sanitizeQdrantMetadata(metadata);
            String embeddingModelName = resolveEmbeddingModelName(chunk.getKnowledgeId());
            EmbeddingModelContext.runWithModel(embeddingModelName, () -> qdrantVectorStore.add(List.of(
                    Document.builder()
                            .id(chunk.getQdrantPointId())
                            .text(req.getContent())
                            .metadata(qdrantMetadata)
                            .build()
            )));

            // 2) 更新DB内容
            chunk.setChunkContent(req.getContent());
            chunk.setChunkLength(req.getContent().length());
            documentChunksService.updateById(chunk);

            // 3) 同步更新 documents.content，避免“片段已改但详情仍显示旧内容/重建覆盖片段修改”
            syncDocumentContentFromChunks(chunk.getDocumentId());
            return Result.success("更新成功");
        } catch (Exception e) {
            return Result.error("更新失败：" + e.getMessage());
        }
    }

    @PostMapping("/remove/{id}")
    public Result<String> remove(@PathVariable("id") Long id) {
        if (id == null) {
            return Result.error(400, "id不能为空");
        }
        DocumentChunks chunk = documentChunksService.getById(id);
        if (chunk == null) {
            return Result.error(404, "未找到片段");
        }

        try {
            // 1) 删除向量（优先按 pointId 删除）
            if (StringUtils.hasText(chunk.getQdrantPointId())) {
                qdrantVectorStore.delete(List.of(chunk.getQdrantPointId()));
            } else {
                deleteChunkVectorsByFilter(chunk);
            }
        } catch (Exception e) {
            return Result.error("删除片段向量失败：" + e.getMessage());
        }

        boolean ok = documentChunksService.removeById(id);
        if (!ok) {
            return Result.error("删除失败");
        }

        try {
            syncDocumentContentFromChunks(chunk.getDocumentId());
        } catch (Exception ignored) {
        }
        return Result.success("删除成功");
    }

    @Data
    public static class FragmentUpdateRequest {
        private Long id;
        private String content;
    }

    private void deleteChunkVectorsByFilter(DocumentChunks chunk) {
        if (chunk == null || chunk.getDocumentId() == null) {
            return;
        }

        Exception numericError = null;
        try {
            Filter.Expression expression = buildChunkFilterExpression(chunk, true);
            if (expression != null) {
                qdrantVectorStore.delete(expression);
            }
        } catch (Exception e) {
            numericError = e;
        }

        try {
            Filter.Expression expression = buildChunkFilterExpression(chunk, false);
            if (expression != null) {
                qdrantVectorStore.delete(expression);
            }
        } catch (Exception e) {
            if (numericError != null) {
                numericError.addSuppressed(e);
                throw new RuntimeException(numericError);
            }
            throw e;
        }
    }

    private Filter.Expression buildChunkFilterExpression(DocumentChunks chunk, boolean preferNumeric) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();

        Object knowledgeIdValue = null;
        if (chunk.getKnowledgeId() != null) {
            knowledgeIdValue = preferNumeric ? sanitizeQdrantValue(chunk.getKnowledgeId()) : String.valueOf(chunk.getKnowledgeId());
        }
        Object documentIdValue = preferNumeric ? sanitizeQdrantValue(chunk.getDocumentId()) : String.valueOf(chunk.getDocumentId());

        FilterExpressionBuilder.Op op = builder.eq("document_id", documentIdValue);
        if (knowledgeIdValue != null) {
            op = builder.and(builder.eq("knowledge_id", knowledgeIdValue), op);
        }

        Object chunkIndexValue = null;
        if (chunk.getChunkIndex() != null) {
            chunkIndexValue = preferNumeric ? sanitizeQdrantValue(chunk.getChunkIndex()) : String.valueOf(chunk.getChunkIndex());
        }
        if (chunkIndexValue != null) {
            op = builder.and(op, builder.eq("chunk_index", chunkIndexValue));
        }

        return op.build();
    }

    private void syncDocumentContentFromChunks(Long documentId) {
        if (documentId == null) {
            return;
        }

        Documents documents = documentsService.getById(documentId);
        if (documents == null) {
            return;
        }

        List<String> chunkContents = documentChunksService.lambdaQuery()
                .select(DocumentChunks::getChunkIndex, DocumentChunks::getChunkContent)
                .eq(DocumentChunks::getDocumentId, documentId)
                .orderByAsc(DocumentChunks::getChunkIndex)
                .list()
                .stream()
                .map(DocumentChunks::getChunkContent)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());

        if (chunkContents.isEmpty()) {
            return;
        }

        String merged = mergeChunkContents(chunkContents);
        if (!StringUtils.hasText(merged)) {
            return;
        }

        documents.setContent(merged);
        documents.setUpdateTime(LocalDateTime.now());
        documentsService.updateById(documents);
    }

    private static String mergeChunkContents(List<String> chunkContents) {
        String merged = null;
        for (String chunk : chunkContents) {
            if (!StringUtils.hasText(chunk)) {
                continue;
            }
            if (merged == null) {
                merged = chunk;
                continue;
            }

            int overlap = findOverlapSuffixPrefix(merged, chunk, 2000);
            merged = overlap > 0 ? (merged + chunk.substring(overlap)) : (merged + chunk);
        }
        return merged == null ? "" : merged;
    }

    private static int findOverlapSuffixPrefix(String left, String right, int maxOverlap) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return 0;
        }
        int max = Math.min(Math.min(left.length(), right.length()), Math.max(0, maxOverlap));
        for (int len = max; len > 0; len--) {
            if (left.regionMatches(left.length() - len, right, 0, len)) {
                return len;
            }
        }
        return 0;
    }

    private Map<String, Object> sanitizeQdrantMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new HashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            String key = entry.getKey();
            if (!StringUtils.hasText(key)) {
                continue;
            }
            Object value = sanitizeQdrantValue(entry.getValue());
            if (value != null) {
                sanitized.put(key, value);
            }
        }
        return sanitized;
    }

    private Object sanitizeQdrantValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String || value instanceof Integer || value instanceof Double || value instanceof Float || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Long longValue) {
            if (longValue <= Integer.MAX_VALUE && longValue >= Integer.MIN_VALUE) {
                return longValue.intValue();
            }
            return longValue.toString();
        }
        if (value instanceof Short shortValue) {
            return shortValue.intValue();
        }
        if (value instanceof Byte byteValue) {
            return byteValue.intValue();
        }
        return value.toString();
    }

    private String resolveEmbeddingModelName(Long knowledgeId) {
        if (knowledgeId == null) {
            return null;
        }
        try {
            var info = knowledgeInfoService.getById(knowledgeId);
            if (info == null) {
                return null;
            }
            String modelName = info.getEmbeddingModelName();
            return StringUtils.hasText(modelName) ? modelName.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
