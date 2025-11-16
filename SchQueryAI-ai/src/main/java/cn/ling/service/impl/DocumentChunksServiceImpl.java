package cn.ling.service.impl;

import cn.ling.vector.CustomQdrantVectorStore;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.ling.domain.pojo.DocumentChunks;
import cn.ling.service.DocumentChunksService;
import cn.ling.mapper.DocumentChunksMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    CustomQdrantVectorStore customQdrantVectorStore;

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
            Document document = new Document(content, metadata);
            List<Document> documentList = tokenTextSplitter.split(document);
            log.info("文档分块完成 - 文档ID: {}, 总分块数: {}", id, documentList.size());

            if (documentList.isEmpty()) {
                log.warn("文档分块结果为空 - 文档ID: {}", id);
                return;
            }

            // 3. 构建文档分块实体列表
            AtomicInteger atomicInteger = new AtomicInteger(1);
            List<DocumentChunks> documentChunks = documentList.stream()
                    .map(doc -> {
                        // 在元数据中添加分块索引
                        doc.getMetadata().put("chunk_index", atomicInteger.get());

                        return DocumentChunks.builder()
                                .documentId(id)
                                .chunkIndex(atomicInteger.getAndIncrement())
                                .chunkContent(doc.getText())
                                .chunkLength(doc.getText().length())
                                .createdTime(LocalDateTime.now())
                                .metadata(metadata)
                                .build();
                    }).toList();

            // 4. 将分片向量化存储到Qdrant
            log.info("开始将文档分块向量化存储到Qdrant - 文档ID: {}, 分块数: {}", id, documentList.size());
            List<String> qdrantPointIds;
            try {
                qdrantPointIds = customQdrantVectorStore.addDocuments(documentList);
                log.info("文档分块向量化存储成功 - 文档ID: {}, Qdrant点ID数量: {}", id, qdrantPointIds.size());
            } catch (Exception e) {
                log.error("文档分块向量化存储失败 - 文档ID: {}, 错误信息: {}", id, e.getMessage(), e);
                throw new RuntimeException("向量化存储失败：" + e.getMessage(), e);
            }

            // 5. 将Qdrant点ID关联到分块实体
            if (qdrantPointIds.size() != documentChunks.size()) {
                log.error("Qdrant返回的点ID数量与分块数量不匹配 - 文档ID: {}, Qdrant点数: {}, 分块数: {}",
                        id, qdrantPointIds.size(), documentChunks.size());
                throw new RuntimeException("Qdrant存储数量不匹配");
            }

            for (int i = 0; i < documentChunks.size(); i++) {
                documentChunks.get(i).setQdrantPointId(qdrantPointIds.get(i));
            }

            // 6. 批量保存分块信息到数据库
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




