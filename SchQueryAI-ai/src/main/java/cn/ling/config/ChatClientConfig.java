package cn.ling.config;

import cn.ling.advisor.IntentRecognizerAdvisor;
import cn.ling.advisor.SensitiveFilterAdvisor;
import cn.ling.prompt.Prompts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ChatClientConfig {

    /**
     * 配置OpenAI聊天客户端
     * 创建ChatClient实例，设置系统提示词和敏感词过滤器
     *
     * @param openAiChatModel OpenAI聊天模型实例
     * @return 配置好的ChatClient聊天客户端
     */
    @Bean("openAiChatClient")
    public ChatClient openAiChatClient(
            OpenAiChatModel openAiChatModel,
            @Qualifier("questionAnswerAdvisor") Advisor questionAnswerAdvisor,
            SensitiveFilterAdvisor sensitiveFilterAdvisor,
            IntentRecognizerAdvisor intentRecognizerAdvisor
    ) {
        log.info("开始配置OpenAI-聊天客户端");
        return ChatClient.builder(openAiChatModel)
                .defaultSystem(Prompts.SYSTEM_PROMPT)
                .defaultAdvisors(
                        questionAnswerAdvisor,
                        intentRecognizerAdvisor,
                        sensitiveFilterAdvisor
                )
                .build();
    }

    @Bean("ocrCorrectChatClient")
    public ChatClient ocrCorrectChatClient(OpenAiChatModel openAiChatModel){
        log.info("开始配置OpenAI-OCR结果纠正客户端");
        return ChatClient.builder(openAiChatModel)
                .defaultSystem(Prompts.OCR_PROMPT)
                .build();
    }

}
