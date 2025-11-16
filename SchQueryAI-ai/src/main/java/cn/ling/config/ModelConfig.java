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

@Slf4j
@Configuration
public class ModelConfig {

    /**
     * 配置OpenAI聊天模型
     * 创建聊天模型实例，集成API和聊天选项
     *
     * @param openAiApi OpenAI API实例
     * @param openAiChatOptions 聊天选项配置
     * @return OpenAiChatModel聊天模型实例
     */
    @Bean
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi, OpenAiChatOptions openAiChatOptions){
        log.info("开始配置OpenAI聊天模型");

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(openAiChatOptions)
                .build();

        log.info("OpenAI聊天模型配置完成");
        return chatModel;
    }

    @Bean
    public EmbeddingModel embeddingModel(OpenAiApi openAiApi, OpenAiEmbeddingOptions openAiEmbeddingOptions) {
        return new OpenAiEmbeddingModel(
                openAiApi,
                MetadataMode.ALL,
                openAiEmbeddingOptions
        );
    }
}
