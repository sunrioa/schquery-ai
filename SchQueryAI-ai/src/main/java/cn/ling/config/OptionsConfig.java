package cn.ling.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI模型选项配置类
 * 配置OpenAI聊天模型和嵌入模型的参数选项
 * 从配置文件中读取模型名称、向量维度等参数
 */
@Slf4j
@Configuration
public class OptionsConfig {
    
    /**
     * 配置OpenAI聊天选项
     * 设置AI聊天模型的基本参数和选项
     * 包括模型名称、温度、最大回复长度等
     *
     * @param model AI模型名称（从配置文件读取）
     * @return OpenAiChatOptions配置对象
     */
    @Bean
    public OpenAiChatOptions openAiChatOptions(@Value("${spring.ai.openai.chat.options.model}") String model){
        log.info("开始配置OpenAI聊天选项 - 模型: {}", model);

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)  // 设置聊天模型名称（如gpt-3.5-turbo, gpt-4等）
                .build();

        log.info("OpenAI聊天选项配置完成 - 使用模型: {}", model);
        return options;
    }

    /**
     * 配置OpenAI嵌入模型选项
     * 设置文本向量化模型的参数
     * 向量维度必须与Qdrant向量数据库的集合配置保持一致
     * 
     * @param model 嵌入模型名称（从配置文件读取）
     * @param dimensions 向量维度（从配置文件读取）
     * @return OpenAiEmbeddingOptions配置对象
     */
    @Bean
    public OpenAiEmbeddingOptions openAiEmbeddingOptions(
            @Value("${spring.ai.openai.embedding.options.model}") String model,
            @Value("${spring.ai.openai.embedding.options.dimensions}") Integer dimensions){
        log.info("开始配置OpenAI嵌入模型选项 - 模型: {}, 向量维度: {}", model, dimensions);
        
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(model)  // 设置嵌入模型名称（如text-embedding-ada-002）
                .dimensions(dimensions)  // 设置向量维度
                .build();
        
        log.info("OpenAI嵌入模型选项配置完成 - 模型: {}, 向量维度: {}", model, dimensions);
        return options;
    }
}
