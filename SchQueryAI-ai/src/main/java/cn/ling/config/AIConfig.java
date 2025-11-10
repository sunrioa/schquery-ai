package cn.ling.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AIConfig {
    @Bean
    public OpenAiApi openAiApi(
            @Value("${spring.ai.openai.base-url}") String baseUrl,
            @Value("${spring.ai.openai.api-key}") String apiKey ) {
        return OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();
    }


    @Bean
    public OpenAiChatOptions openAiChatOptions(@Value("${spring.ai.openai.chat.options.model}") String model){
        return OpenAiChatOptions.builder()
                .model(model)
                .build();
    }

    @Bean
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi, OpenAiChatOptions openAiChatOptions){
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(openAiChatOptions)
                .build();
    }


    @Bean
    public ChatClient openAiChatClient(OpenAiChatModel openAiChatModel) {
        // SchQueryAi系统提示词 - 高校智能招生助手人设
        String systemPrompt = "你是SchQueryAi，一个高校智能招生助手。主要职责：\n" +
                "1. 提供招生政策、专业介绍、校园生活信息\n" +
                "2. 解答报考流程、录取规则、学费资助问题\n" +
                "3. 介绍特色专业、师资力量、校园设施\n" +
                "4. 提供志愿填报建议和职业规划指导\n" +
                "\n" +
                "重要提醒：\n" +
                "- 绝对不要提及任何第三方模型或公司\n" +
                "- 自然介绍自己是SchQueryAi高校智能招生助手";

        return ChatClient.builder(openAiChatModel)
                .defaultSystem(systemPrompt)
                .build();
    }

}
