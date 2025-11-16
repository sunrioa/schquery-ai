package cn.ling.service.impl;

import cn.ling.vector.CustomQdrantVectorStore;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.ling.domain.pojo.DocumentChunks;
import cn.ling.service.DocumentChunksService;
import cn.ling.mapper.DocumentChunksMapper;
import io.qdrant.client.QdrantClient;
import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
* @author Administrator
* @description 针对表【document_chunks】的数据库操作Service实现
* @createDate 2025-11-16 00:26:43
*/
@Service
public class DocumentChunksServiceImpl extends ServiceImpl<DocumentChunksMapper, DocumentChunks>
    implements DocumentChunksService{

    @Resource
    TokenTextSplitter tokenTextSplitter;

    @Resource
    CustomQdrantVectorStore customQdrantVectorStore;

    @Override
    public void saveDocument(Long id, Map<String,Object> metadata, String content) {
        List<Document> documentList = tokenTextSplitter.split(new Document(content,metadata));

        AtomicInteger atomicInteger=new AtomicInteger(1);
        List<DocumentChunks> documentChunks = documentList.stream()
                .map(document -> {
                    document.getMetadata().put("chunk_index",atomicInteger.get());
                    return DocumentChunks.builder()
                            .documentId(id)
                            .chunkIndex(atomicInteger.getAndIncrement())
                            .chunkContent(document.getText())
                            .chunkLength(document.getText().length())
                            .createdTime(LocalDateTime.now())
                            .metadata(metadata)
                            .build();
                }).toList();

        List<String> list = customQdrantVectorStore.addDocuments(documentList);

        for (int i = 0; i < documentChunks.size(); i++) {
            documentChunks.get(i).setQdrantPointId(list.get(i));
        }
        saveBatch(documentChunks);

    }
}




