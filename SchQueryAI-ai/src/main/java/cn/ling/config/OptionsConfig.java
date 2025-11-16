package cn.ling.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class OptionsConfig {
    /**
     * 配置OpenAI聊天选项
     * 设置AI聊天模型的基本参数和选项
     *
     * @param model AI模型名称（从配置文件读取）
     * @return OpenAiChatOptions配置对象
     */
    @Bean
    public OpenAiChatOptions openAiChatOptions(@Value("${spring.ai.openai.chat.options.model}") String model){
        log.info("开始配置OpenAI聊天选项，模型: {}", model);

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .build();

        log.info("OpenAI聊天选项配置完成，使用模型: {}", model);
        return options;
    }

    @Bean
    public OpenAiEmbeddingOptions openAiEmbeddingOptions(
            @Value("${spring.ai.openai.embedding.options.model}") String model,
            @Value("${spring.ai.openai.embedding.options.dimensions}") Integer dimensions){
        return OpenAiEmbeddingOptions.builder()
                .model(model)
                .dimensions(dimensions)
                .build();
    }
}
