package cn.ling.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI模型配置类
 * 配置OpenAI的聊天模型和嵌入模型
 * 聊天模型用于对话生成，嵌入模型用于文本向量化
 */
@Slf4j
@Configuration
public class ModelConfig {

    /**
     * 配置OpenAI聊天模型
     * 创建聊天模型实例，集成API和聊天选项
     * 用于生成AI对话响应、回答用户问题
     *
     * @param openAiApi OpenAI API实例，用于与模型服务通信
     * @param openAiChatOptions 聊天选项配置，包含模型名称、参数等
     * @return OpenAiChatModel聊天模型实例
     */
    @Bean
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi, OpenAiChatOptions openAiChatOptions){
        log.info("开始配置OpenAI聊天模型");

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)  // 设置API客户端
                .defaultOptions(openAiChatOptions)  // 设置默认选项
                .build();

        log.info("OpenAI聊天模型配置完成");
        return chatModel;
    }

    /**
     * 配置OpenAI嵌入模型
     * 创建嵌入模型实例，用于将文本转换为向量表示
     * 支持RAG功能，用于文档相似度检索和语义搜索
     * 
     * @param openAiApi OpenAI API实例
     * @param openAiEmbeddingOptions 嵌入模型选项配置
     * @return EmbeddingModel嵌入模型实例
     */
    @Bean
    public EmbeddingModel embeddingModel(OpenAiApi openAiApi, OpenAiEmbeddingOptions openAiEmbeddingOptions) {
        log.info("开始配置OpenAI嵌入模型 - 模型: {}, 向量维度: {}", 
                openAiEmbeddingOptions.getModel(), openAiEmbeddingOptions.getDimensions());
        
        EmbeddingModel embeddingModel = new OpenAiEmbeddingModel(
                openAiApi,
                MetadataMode.ALL,  // 包含所有元数据信息
                openAiEmbeddingOptions
        );
        
        log.info("OpenAI嵌入模型配置完成");
        return embeddingModel;
    }
}
