package cn.ling.config;

import cn.ling.vector.CustomQdrantVectorStore;
import io.qdrant.client.QdrantClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class VectorStoreConfig {

    @Bean
    public VectorStore qdrantVectorStore(
            QdrantClient qdrantClient,
            EmbeddingModel embeddingModel,
            @Value("${spring.ai.vectorstore.qdrant.collection-name}") String collectionName,
            @Value("${spring.ai.vectorstore.qdrant.initialize-schema}") Boolean initializeSchema
    ) {
        return QdrantVectorStore.builder(qdrantClient, embeddingModel)
                .batchingStrategy(new TokenCountBatchingStrategy())
                .collectionName(collectionName)
                .initializeSchema(initializeSchema)
                .build();
    }

    @Bean
    public CustomQdrantVectorStore customQdrantVectorStore(
            QdrantClient qdrantClient,
            EmbeddingModel embeddingModel,
            @Value("${spring.ai.vectorstore.qdrant.collection-name}") String collectionName,
            @Value("${spring.ai.vectorstore.qdrant.initialize-schema}") Boolean initializeSchema
    ){
        return new CustomQdrantVectorStore(qdrantClient, embeddingModel,collectionName,initializeSchema);
    }

}
