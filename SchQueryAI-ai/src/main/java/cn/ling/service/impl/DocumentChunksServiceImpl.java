package cn.ling.service.impl;

import cn.ling.embedding.EmbeddingModelContext;
import cn.ling.utils.TextSplitterUtils;
import cn.ling.domain.pojo.KnowledgeInfo;
import cn.ling.service.KnowledgeInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.ling.domain.pojo.DocumentChunks;
import cn.ling.service.DocumentChunksService;
import cn.ling.mapper.DocumentChunksMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文档分块服务实现类
 * 负责文档的分块处理和向量化存储
 */
@Slf4j
@Service
public class DocumentChunksServiceImpl extends ServiceImpl<DocumentChunksMapper, DocumentChunks>
    implements DocumentChunksService{

    @Resource
    TokenTextSplitter tokenTextSplitter;

    @Resource
    @Qualifier("qdrantVectorStore")
    VectorStore qdrantVectorStore;

    @Resource
    TextSplitterUtils textSplitterUtils;

    @Resource
    private KnowledgeInfoService knowledgeInfoService;

    /**
     * 保存文档分块
     * 1. 使用TokenTextSplitter将文档内容分割成多个片段
     * 2. 将片段向量化存储到Qdrant
     * 3. 将片段信息保存到数据库
     *
     * @param id 文档ID
     * @param knowledgeId 知识库ID（knowledge_info.id）
     * @param metadata 文档元数据
     * @param content 文档内容
     */
    @Override
    public void saveDocument(Long id, Long knowledgeId, Map<String, Object> metadata, String content) {
        log.info("开始处理文档分块 - 文档ID: {}, 内容长度: {} 字符", id, content != null ? content.length() : 0);

        try {
            // 1. 参数校验
            if (id == null) {
                log.error("文档分块保存失败：文档ID为空");
                throw new IllegalArgumentException("文档ID不能为空");
            }

            if (!StringUtils.hasText(content)) {
                log.warn("文档内容为空 - 文档ID: {}", id);
                return;
            }

            // 2. 文档分块处理
            TextSplitterUtils splitter = resolveTextSplitter(knowledgeId);
            List<String> chunkTexts = splitter.splitText(content);
            log.info("文档分块完成 - 文档ID: {}, 总分块数: {}", id, chunkTexts.size());

            if (chunkTexts.isEmpty()) {
                log.warn("文档分块结果为空 - 文档ID: {}", id);
                return;
            }

            Map<String, Object> baseMetadata = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
            baseMetadata.putIfAbsent("document_id", id);
            if (knowledgeId != null) {
                baseMetadata.putIfAbsent("knowledge_id", knowledgeId);
            }

            Map<String, Object> baseMetadataForQdrant = sanitizeQdrantMetadata(baseMetadata);

            // 3. 构建待写入Qdrant的Document与DB分块实体（元数据必须逐块拷贝，避免被覆盖）
            AtomicInteger chunkIndex = new AtomicInteger(1);
            List<Document> qdrantDocuments = new ArrayList<>(chunkTexts.size());
            List<DocumentChunks> documentChunks = new ArrayList<>(chunkTexts.size());

            for (String chunkText : chunkTexts) {
                if (!StringUtils.hasText(chunkText)) {
                    continue;
                }

                int index = chunkIndex.getAndIncrement();
                Map<String, Object> chunkMetadata = new HashMap<>(baseMetadata);
                chunkMetadata.put("chunk_index", index);

                Map<String, Object> chunkMetadataForQdrant = new HashMap<>(baseMetadataForQdrant);
                chunkMetadataForQdrant.put("chunk_index", index);

                String pointId = UUID.randomUUID().toString();
                Document qdrantDocument = Document.builder()
                        .id(pointId)
                        .text(chunkText)
                        .metadata(chunkMetadataForQdrant)
                        .build();
                qdrantDocuments.add(qdrantDocument);

                documentChunks.add(DocumentChunks.builder()
                        .documentId(id)
                        .knowledgeId(knowledgeId)
                        .chunkIndex(index)
                        .chunkContent(chunkText)
                        .chunkLength(chunkText.length())
                        .createdTime(LocalDateTime.now())
                        .qdrantPointId(pointId)
                        .metadata(chunkMetadata)
                        .build());
            }

            if (qdrantDocuments.isEmpty()) {
                log.warn("过滤空白片段后无有效分块 - 文档ID: {}", id);
                return;
            }

            // 4. 将分片向量化存储到Qdrant（使用Spring AI标准VectorStore，确保与检索端一致）
            log.info("开始将文档分块向量化存储到Qdrant - 文档ID: {}, 分块数: {}", id, qdrantDocuments.size());
            try {
                String embeddingModelName = resolveEmbeddingModelName(knowledgeId);
                EmbeddingModelContext.runWithModel(embeddingModelName, () -> qdrantVectorStore.add(qdrantDocuments));
                log.info("文档分块向量化存储成功 - 文档ID: {}, 分块数: {}", id, qdrantDocuments.size());
            } catch (Exception e) {
                log.error("文档分块向量化存储失败 - 文档ID: {}, 错误信息: {}", id, e.getMessage(), e);
                throw new RuntimeException("向量化存储失败：" + e.getMessage(), e);
            }

            // 5. 批量保存分块信息到数据库
            boolean saved = saveBatch(documentChunks);
            if (saved) {
                log.info("文档分块保存成功 - 文档ID: {}, 保存的分块数: {}", id, documentChunks.size());
            } else {
                log.error("文档分块保存失败 - 文档ID: {}", id);
                throw new RuntimeException("分块保存失败");
            }

        } catch (Exception e) {
            log.error("文档分块处理过程中发生异常 - 文档ID: {}, 错误信息: {}", id, e.getMessage(), e);
            throw new RuntimeException("文档分块处理失败：" + e.getMessage(), e);
        }
    }

    /**
     * QdrantVectorStore 的 payload 不支持 Long 等部分 Java 类型，需要在入库前做一次安全转换。
     * <p>
     * 目标：保证 payload 只包含 QdrantValueFactory 支持的类型（如 String/Integer/Double/Boolean/List/Map）。
     */
    private static Map<String, Object> sanitizeQdrantMetadata(Map<String, Object> metadata) {
        Map<String, Object> sanitized = new HashMap<>();
        if (metadata == null || metadata.isEmpty()) {
            return sanitized;
        }
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

    private static Object sanitizeQdrantValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String || value instanceof Boolean || value instanceof Integer || value instanceof Double) {
            return value;
        }
        if (value instanceof Float f) {
            return f.doubleValue();
        }
        if (value instanceof Short s) {
            return s.intValue();
        }
        if (value instanceof Byte b) {
            return b.intValue();
        }
        if (value instanceof Long l) {
            if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                return l.intValue();
            }
            // 超出 int 范围时使用字符串，避免精度丢失和类型不支持
            return String.valueOf(l);
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new HashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String nestedKey = e.getKey() == null ? null : String.valueOf(e.getKey());
                if (!StringUtils.hasText(nestedKey)) {
                    continue;
                }
                Object nestedValue = sanitizeQdrantValue(e.getValue());
                if (nestedValue != null) {
                    nested.put(nestedKey, nestedValue);
                }
            }
            return nested;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> list = new ArrayList<>();
            for (Object item : iterable) {
                Object safe = sanitizeQdrantValue(item);
                if (safe != null) {
                    list.add(safe);
                }
            }
            return list;
        }
        if (value instanceof Object[] arr) {
            List<Object> list = new ArrayList<>(arr.length);
            for (Object item : arr) {
                Object safe = sanitizeQdrantValue(item);
                if (safe != null) {
                    list.add(safe);
                }
            }
            return list;
        }

        // 兜底：转成字符串，避免抛出 Unsupported value type
        return String.valueOf(value);
    }

    private TextSplitterUtils resolveTextSplitter(Long knowledgeId) {
        int chunkSize = 600;
        int chunkOverlap = 100;

        try {
            if (knowledgeId != null) {
                KnowledgeInfo knowledgeInfo = knowledgeInfoService.getById(knowledgeId);
                if (knowledgeInfo != null) {
                    if (knowledgeInfo.getTextBlockSize() != null && knowledgeInfo.getTextBlockSize() > 0) {
                        chunkSize = knowledgeInfo.getTextBlockSize();
                    }
                    if (knowledgeInfo.getOverlapChar() != null && knowledgeInfo.getOverlapChar() >= 0) {
                        chunkOverlap = knowledgeInfo.getOverlapChar();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("读取知识库分块参数失败，将使用默认值: {}", e.getMessage());
        }

        if (chunkSize <= 0) {
            chunkSize = 600;
        }
        if (chunkOverlap < 0) {
            chunkOverlap = 0;
        }
        if (chunkOverlap >= chunkSize) {
            chunkOverlap = Math.max(0, chunkSize / 3);
        }

        if (chunkSize == textSplitterUtils.getChunkSize() && chunkOverlap == textSplitterUtils.getChunkOverlap()) {
            return textSplitterUtils;
        }

        return new TextSplitterUtils.Builder()
                .separators(textSplitterUtils.getSeparators())
                .chunkSize(chunkSize)
                .chunkOverlap(chunkOverlap)
                .lengthFunction(textSplitterUtils.getLengthFunction())
                .build();
    }

    private String resolveEmbeddingModelName(Long knowledgeId) {
        if (knowledgeId == null) {
            return null;
        }
        try {
            KnowledgeInfo knowledgeInfo = knowledgeInfoService.getById(knowledgeId);
            if (knowledgeInfo == null) {
                return null;
            }
            String modelName = knowledgeInfo.getEmbeddingModelName();
            return StringUtils.hasText(modelName) ? modelName.trim() : null;
        } catch (Exception e) {
            log.debug("读取知识库 embedding_model_name 失败，将使用默认模型: {}", e.getMessage());
            return null;
        }
    }
}

