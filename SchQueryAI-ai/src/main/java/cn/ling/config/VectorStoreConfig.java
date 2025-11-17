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

/**
 * 向量存储配置类
 * 配置Qdrant向量数据库相关组件，提供标准和自定义的向量存储实现
 * 用于支持RAG（检索增强生成）功能，存储和检索文档向量
 */
@Slf4j
@Configuration
public class VectorStoreConfig {

    /**
     * 配置Qdrant向量存储实例（Spring AI标准实现）
     * 使用Spring AI提供的标准QdrantVectorStore实现
     * 支持批处理策略，优化向量化性能
     * 
     * @param qdrantClient Qdrant客户端实例
     * @param embeddingModel 嵌入模型实例
     * @param collectionName 集合名称（从配置文件读取）
     * @param initializeSchema 是否初始化集合架构（从配置文件读取）
     * @return VectorStore向量存储实例
     */
    @Bean
    public VectorStore qdrantVectorStore(
            QdrantClient qdrantClient,
            EmbeddingModel embeddingModel,
            @Value("${spring.ai.vectorstore.qdrant.collection-name}") String collectionName,
            @Value("${spring.ai.vectorstore.qdrant.initialize-schema}") Boolean initializeSchema
    ) {
        log.info("开始配置Qdrant向量存储 - 集合名称: {}, 初始化架构: {}", collectionName, initializeSchema);
        
        VectorStore vectorStore = QdrantVectorStore.builder(qdrantClient, embeddingModel)
                .batchingStrategy(new TokenCountBatchingStrategy()) // 使用Token计数批处理策略，优化批量处理性能
                .collectionName(collectionName)
                .initializeSchema(initializeSchema)
                .build();
        
        log.info("Qdrant向量存储配置完成 - 集合名称: {}", collectionName);
        return vectorStore;
    }

    /**
     * 配置自定义Qdrant向量存储实例
     * 使用自定义实现，提供更灵活的向量操作能力
     * 支持更细粒度的文档管理、检索和删除操作
     * 
     * @param qdrantClient Qdrant客户端实例
     * @param embeddingModel 嵌入模型实例
     * @param collectionName 集合名称（从配置文件读取）
     * @param initializeSchema 是否初始化集合架构（从配置文件读取）
     * @return CustomQdrantVectorStore自定义向量存储实例
     */
    @Bean
    public CustomQdrantVectorStore customQdrantVectorStore(
            QdrantClient qdrantClient,
            EmbeddingModel embeddingModel,
            @Value("${spring.ai.vectorstore.qdrant.collection-name}") String collectionName,
            @Value("${spring.ai.vectorstore.qdrant.initialize-schema}") Boolean initializeSchema
    ){
        log.info("开始配置自定义Qdrant向量存储 - 集合名称: {}, 初始化架构: {}", collectionName, initializeSchema);
        
        CustomQdrantVectorStore customVectorStore = new CustomQdrantVectorStore(qdrantClient, embeddingModel, collectionName, initializeSchema);
        
        log.info("自定义Qdrant向量存储配置完成 - 集合名称: {}", collectionName);
        return customVectorStore;
    }
}
