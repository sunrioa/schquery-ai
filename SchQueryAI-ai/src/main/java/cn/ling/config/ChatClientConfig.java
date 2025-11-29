package cn.ling.config;

import cn.ling.advisor.IntentRecognizerAdvisor;
import cn.ling.advisor.SensitiveFilterAdvisor;
import cn.ling.prompt.Prompts;
import cn.ling.tools.ChatTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 聊天客户端配置类
 * 配置不同业务场景的ChatClient实例
 * 支持多种顾问组合，实现敏感词过滤、意图识别、RAG增强等功能
 */
@Slf4j
@Configuration
public class ChatClientConfig {

    /**
     * 配置OpenAI聊天客户端（主要业务客户端）
     * 创建ChatClient实例，配置系统提示词和多个顾问
     * 顾问执行顺序：敏感词过滤(0) -> 意图识别(1) -> RAG增强(2)
     *
     * @param openAiChatModel OpenAI聊天模型实例
     * @param questionAnswerAdvisor RAG问答增强顾问，基于向量数据库检索相关文档
     * @param sensitiveFilterAdvisor 敏感词过滤顾问，过滤用户输入中的敏感内容
     * @param intentRecognizerAdvisor 意图识别顾问，识别用户查询意图
     * @return 配置好的ChatClient聊天客户端
     */
    @Bean("openAiChatClient")
    public ChatClient openAiChatClient(
            OpenAiChatModel openAiChatModel,
            @Qualifier("questionAnswerAdvisor") Advisor questionAnswerAdvisor,
            SensitiveFilterAdvisor sensitiveFilterAdvisor,
            IntentRecognizerAdvisor intentRecognizerAdvisor,
            ChatTools chatTools
    ) {
        log.info("开始配置OpenAI聊天客户端");
        
        ChatClient chatClient = ChatClient.builder(openAiChatModel)
                .defaultSystem(Prompts.SYSTEM_PROMPT)  // 设置默认系统提示词，定义AI助手角色
                .defaultAdvisors(
                        questionAnswerAdvisor,      // RAG问答增强，顺序: 2
                        intentRecognizerAdvisor,    // 意图识别，顺序: 1
                        sensitiveFilterAdvisor      // 敏感词过滤，顺序: 0
                )
                .defaultTools(chatTools)
                .build();
        
        log.info("OpenAI聊天客户端配置完成，已启用顾问: RAG增强, 意图识别, 敏感词过滤");
        return chatClient;
    }

    /**
     * 配置OCR结果纠正客户端
     * 专门用于OCR识别结果的后处理，修正识别错误和格式问题
     * 不启用任何顾问，保持简单的文本处理流程
     *
     * @param openAiChatModel OpenAI聊天模型实例
     * @return 配置好的ChatClient聊天客户端
     */
    @Bean("ocrCorrectChatClient")
    public ChatClient ocrCorrectChatClient(OpenAiChatModel openAiChatModel){
        log.info("开始配置OCR结果纠正客户端");
        
        ChatClient chatClient = ChatClient.builder(openAiChatModel)
                .defaultSystem(Prompts.OCR_PROMPT)  // 设置OCR专用系统提示词
                .build();
        
        log.info("OCR结果纠正客户端配置完成");
        return chatClient;
    }
}
