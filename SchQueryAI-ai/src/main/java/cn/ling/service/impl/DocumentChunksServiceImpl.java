package cn.ling.service.impl;

import cn.ling.utils.TextSplitterUtils;
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

    /**
     * 保存文档分块
     * 1. 使用TokenTextSplitter将文档内容分割成多个片段
     * 2. 将片段向量化存储到Qdrant
     * 3. 将片段信息保存到数据库
     *
     * @param id 文档ID
     * @param metadata 文档元数据
     * @param content 文档内容
     */
    @Override
    public void saveDocument(Long id, Map<String, Object> metadata, String content) {
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
            List<String> chunkTexts = textSplitterUtils.splitText(content);
            log.info("文档分块完成 - 文档ID: {}, 总分块数: {}", id, chunkTexts.size());

            if (chunkTexts.isEmpty()) {
                log.warn("文档分块结果为空 - 文档ID: {}", id);
                return;
            }

            Map<String, Object> baseMetadata = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
            baseMetadata.putIfAbsent("document_id", id);

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

                String pointId = UUID.randomUUID().toString();
                Document qdrantDocument = Document.builder()
                        .id(pointId)
                        .text(chunkText)
                        .metadata(chunkMetadata)
                        .build();
                qdrantDocuments.add(qdrantDocument);

                documentChunks.add(DocumentChunks.builder()
                        .documentId(id)
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
                qdrantVectorStore.add(qdrantDocuments);
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
}



