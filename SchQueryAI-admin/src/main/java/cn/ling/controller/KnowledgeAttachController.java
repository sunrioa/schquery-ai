package cn.ling.controller;

import cn.ling.Result;
import cn.ling.embedding.EmbeddingModelContext;
import cn.ling.domain.dto.DocumentsDTO;
import cn.ling.domain.pojo.DocumentChunks;
import cn.ling.domain.pojo.Documents;
import cn.ling.dto.KnowledgeDocumentInfoDTO;
import cn.ling.service.DocumentChunksService;
import cn.ling.service.DocumentsService;
import cn.ling.service.KnowledgeInfoService;
import cn.ling.utils.JsonUtils;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 知识库附件（文档）管理
 * - 以 documents/document_chunks 为存储
 * - 向量存储使用单 collection，通过 metadata.knowledge_id/document_id 过滤与删除
 */
@Slf4j
@RestController
@RequestMapping("/knowledge/attach")
public class KnowledgeAttachController {

    @Resource
    private DocumentsService documentsService;

    @Resource
    private DocumentChunksService documentChunksService;

    @Resource
    private KnowledgeInfoService knowledgeInfoService;

    @Resource(name = "qdrantVectorStore")
    private VectorStore qdrantVectorStore;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Long> upload(
            @RequestParam(value = "knowledgeId", required = false) Long knowledgeId,
            @RequestParam(value = "id", required = false) Long id,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "metadata", required = false) String metadataJson,
            @RequestParam("file") MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            return Result.error(400, "文件不能为空");
        }

        DocumentsDTO documentsDTO = new DocumentsDTO();
        documentsDTO.setId(id);
        documentsDTO.setKnowledgeId(knowledgeId);
        documentsDTO.setTitle(title);
        documentsDTO.setContent(content);

        try {
            if (StringUtils.hasText(metadataJson)) {
                documentsDTO.setMetadata(JsonUtils.strToMap(metadataJson));
            }
        } catch (Exception e) {
            log.warn("metadata解析失败，将忽略该字段：{}", e.getMessage());
        }

        return documentsService.upload(documentsDTO, file);
    }

    /**
     * 重新索引指定文档（基于 DB 当前片段内容重算 embedding，不重新切分）
     * - 适用于：更换 embedding 模型 / 修复向量库数据 / 历史数据补写 metadata 等
     */
    @PostMapping("/reindex/{docId}")
    public Result<String> reindex(@PathVariable("docId") Long docId) {
        if (docId == null) {
            return Result.error(400, "docId不能为空");
        }

        Documents documents = documentsService.getById(docId);
        if (documents == null) {
            return Result.error(404, "未找到文档");
        }
        Long knowledgeId = documents.getKnowledgeId();
        if (knowledgeId == null) {
            return Result.error("文档未绑定knowledgeId，无法重建索引");
        }

        List<DocumentChunks> chunks = documentChunksService.lambdaQuery()
                .eq(DocumentChunks::getDocumentId, docId)
                .orderByAsc(DocumentChunks::getChunkIndex)
                .list();
        if (chunks == null || chunks.isEmpty()) {
            return Result.error("该文档暂无分块，无法重建索引");
        }

        try {
            // 1) 标记处理中
            documents.setProcessStatus(1);
            documents.setUpdateTime(LocalDateTime.now());
            documentsService.updateById(documents);

            // 2) 准备 pointIds 与待写入向量
            List<String> pointIds = new ArrayList<>(chunks.size());
            List<Document> qdrantDocs = new ArrayList<>(chunks.size());

            for (DocumentChunks chunk : chunks) {
                if (chunk == null) {
                    continue;
                }
                String text = chunk.getChunkContent();
                if (!StringUtils.hasText(text)) {
                    continue;
                }

                if (!StringUtils.hasText(chunk.getQdrantPointId())) {
                    chunk.setQdrantPointId(UUID.randomUUID().toString());
                }
                if (chunk.getKnowledgeId() == null) {
                    chunk.setKnowledgeId(knowledgeId);
                }
                if (chunk.getChunkLength() == null) {
                    chunk.setChunkLength(text.length());
                }

                Map<String, Object> metadata = chunk.getMetadata() == null ? new HashMap<>() : new HashMap<>(chunk.getMetadata());
                metadata.putIfAbsent("knowledge_id", knowledgeId);
                metadata.putIfAbsent("document_id", docId);
                if (chunk.getChunkIndex() != null) {
                    metadata.putIfAbsent("chunk_index", chunk.getChunkIndex());
                }
                chunk.setMetadata(metadata);

                Map<String, Object> qdrantMetadata = sanitizeQdrantMetadata(metadata);

                pointIds.add(chunk.getQdrantPointId());
                qdrantDocs.add(Document.builder()
                        .id(chunk.getQdrantPointId())
                        .text(text)
                        .metadata(qdrantMetadata)
                        .build());
            }

            // 3) 删除旧向量（按 pointId 更可靠，不依赖 metadata 是否齐全）
            List<String> deleteIds = pointIds.stream().filter(StringUtils::hasText).distinct().collect(Collectors.toList());
            if (!deleteIds.isEmpty()) {
                qdrantVectorStore.delete(deleteIds);
            }

            // 4) 写入新向量
            if (!qdrantDocs.isEmpty()) {
                String embeddingModelName = resolveEmbeddingModelName(knowledgeId);
                EmbeddingModelContext.runWithModel(embeddingModelName, () -> qdrantVectorStore.add(qdrantDocs));
            }

            // 5) 回写可能补齐的 pointId/metadata/knowledgeId/chunkLength
            documentChunksService.updateBatchById(chunks);

            // 6) 标记完成
            documents.setProcessStatus(2);
            documents.setUpdateTime(LocalDateTime.now());
            documentsService.updateById(documents);

            return Result.success("重建索引成功");
        } catch (Exception e) {
            try {
                documents.setProcessStatus(3);
                documents.setUpdateTime(LocalDateTime.now());
                documentsService.updateById(documents);
            } catch (Exception ignored) {
            }
            return Result.error("重建索引失败：" + e.getMessage());
        }
    }

    @GetMapping("/info/{id}")
    public Result<KnowledgeDocumentInfoDTO> info(@PathVariable("id") Long id) {
        if (id == null) {
            return Result.error(400, "文档ID不能为空");
        }
        Documents documents = documentsService.getById(id);
        if (documents == null) {
            return Result.error(404, "未找到文档");
        }

        long chunkCount = documentChunksService.lambdaQuery()
                .eq(DocumentChunks::getDocumentId, id)
                .count();

        KnowledgeDocumentInfoDTO dto = new KnowledgeDocumentInfoDTO(
                documents.getId(),
                documents.getKnowledgeId(),
                documents.getTitle(),
                documents.getContent(),
                documents.getProcessStatus(),
                (int) chunkCount,
                documents.getUpdateTime(),
                documents.getMetadata()
        );
        return Result.success(dto);
    }

    @GetMapping("/list/{knowledgeId}")
    public Result<List<Documents>> listByKnowledge(@PathVariable("knowledgeId") Long knowledgeId) {
        if (knowledgeId == null) {
            return Result.error(400, "knowledgeId不能为空");
        }
        List<Documents> docs = documentsService.lambdaQuery()
                .eq(Documents::getKnowledgeId, knowledgeId)
                .orderByDesc(Documents::getUpdateTime)
                .list();
        return Result.success(docs);
    }

    /**
     * 重建文档向量（删除旧向量与分块 -> 写入新分块与向量）
     * - 用于管理端在编辑文档内容后快速“重建索引”
     */
    @PostMapping("/rebuild")
    public Result<String> rebuild(@RequestBody RebuildRequest req) {
        if (req == null || req.getDocId() == null) {
            return Result.error(400, "docId不能为空");
        }
        if (!StringUtils.hasText(req.getContent())) {
            return Result.error(400, "content不能为空");
        }

        Documents documents = documentsService.getById(req.getDocId());
        if (documents == null) {
            return Result.error(404, "未找到文档");
        }

        Long knowledgeId = documents.getKnowledgeId();
        if (knowledgeId == null) {
            return Result.error("文档未绑定knowledgeId，无法重建");
        }

        try {
            // 1) 标记处理中 & 更新内容
            documents.setContent(req.getContent());
            documents.setProcessStatus(1);
            documents.setUpdateTime(LocalDateTime.now());
            documentsService.updateById(documents);

            // 2) 删除旧向量（优先按 pointId 删除，更可靠）
            List<String> pointIds = documentChunksService.lambdaQuery()
                    .select(DocumentChunks::getQdrantPointId)
                    .eq(DocumentChunks::getDocumentId, documents.getId())
                    .list()
                    .stream()
                    .map(DocumentChunks::getQdrantPointId)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .collect(Collectors.toList());
            if (!pointIds.isEmpty()) {
                qdrantVectorStore.delete(pointIds);
            } else {
                deleteVectorsByFilter(knowledgeId, documents.getId());
            }

            // 3) 删除旧分块
            documentChunksService.lambdaUpdate()
                    .eq(DocumentChunks::getDocumentId, documents.getId())
                    .remove();

            // 4) 重新分块 + 向量化 + 写入分块表
            documentChunksService.saveDocument(documents.getId(), knowledgeId, documents.getMetadata(), req.getContent());

            // 5) 标记完成
            documents.setProcessStatus(2);
            documents.setUpdateTime(LocalDateTime.now());
            documentsService.updateById(documents);

            return Result.success("重建成功");
        } catch (Exception e) {
            try {
                documents.setProcessStatus(3);
                documents.setUpdateTime(LocalDateTime.now());
                documentsService.updateById(documents);
            } catch (Exception ignored) {
            }
            return Result.error("重建失败：" + e.getMessage());
        }
    }

    @PostMapping("/remove/{id}")
    public Result<String> remove(@PathVariable("id") Long id) {
        if (id == null) {
            return Result.error(400, "文档ID不能为空");
        }
        Documents documents = documentsService.getById(id);
        if (documents == null) {
            return Result.error(404, "未找到文档");
        }

        try {
            // 1) 删除向量（优先按 pointId 删除，更可靠）
            List<String> pointIds = documentChunksService.lambdaQuery()
                    .select(DocumentChunks::getQdrantPointId)
                    .eq(DocumentChunks::getDocumentId, id)
                    .list()
                    .stream()
                    .map(DocumentChunks::getQdrantPointId)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .collect(Collectors.toList());
            if (!pointIds.isEmpty()) {
                qdrantVectorStore.delete(pointIds);
            } else {
                deleteVectorsByFilter(documents.getKnowledgeId(), id);
            }

            // 2) 删除分块与文档（DB）
            documentChunksService.lambdaUpdate()
                    .eq(DocumentChunks::getDocumentId, id)
                    .remove();
            documentsService.removeById(id);

            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error("删除失败：" + e.getMessage());
        }
    }

    @Data
    public static class RebuildRequest {
        private Long docId;
        private String content;
    }

    private void deleteVectorsByFilter(Long knowledgeId, Long documentId) {
        if (documentId == null) {
            return;
        }

        Exception numericError = null;
        try {
            qdrantVectorStore.delete(buildFilterExpression(knowledgeId, documentId, true));
        } catch (Exception e) {
            numericError = e;
        }

        try {
            qdrantVectorStore.delete(buildFilterExpression(knowledgeId, documentId, false));
        } catch (Exception e) {
            if (numericError != null) {
                numericError.addSuppressed(e);
                throw new RuntimeException(numericError);
            }
            throw e;
        }
    }

    private Filter.Expression buildFilterExpression(Long knowledgeId, Long documentId, boolean preferNumeric) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();

        Object knowledgeIdValue = preferNumeric ? sanitizeQdrantValue(knowledgeId) : (knowledgeId == null ? null : String.valueOf(knowledgeId));
        Object documentIdValue = preferNumeric ? sanitizeQdrantValue(documentId) : String.valueOf(documentId);

        if (knowledgeIdValue != null) {
            return builder.and(
                    builder.eq("knowledge_id", knowledgeIdValue),
                    builder.eq("document_id", documentIdValue)
            ).build();
        }
        return builder.eq("document_id", documentIdValue).build();
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
