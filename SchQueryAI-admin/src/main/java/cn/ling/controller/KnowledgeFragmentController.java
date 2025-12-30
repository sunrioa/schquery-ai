package cn.ling.controller;

import cn.ling.Result;
import cn.ling.embedding.EmbeddingModelContext;
import cn.ling.domain.pojo.DocumentChunks;
import cn.ling.service.DocumentChunksService;
import cn.ling.service.KnowledgeInfoService;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            String embeddingModelName = resolveEmbeddingModelName(chunk.getKnowledgeId());
            EmbeddingModelContext.runWithModel(embeddingModelName, () -> qdrantVectorStore.add(List.of(
                    Document.builder()
                            .id(chunk.getQdrantPointId())
                            .text(req.getContent())
                            .metadata(metadata)
                            .build()
            )));

            // 2) 更新DB内容
            chunk.setChunkContent(req.getContent());
            chunk.setChunkLength(req.getContent().length());
            documentChunksService.updateById(chunk);
            return Result.success("更新成功");
        } catch (Exception e) {
            return Result.error("更新失败：" + e.getMessage());
        }
    }

    @Data
    public static class FragmentUpdateRequest {
        private Long id;
        private String content;
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
