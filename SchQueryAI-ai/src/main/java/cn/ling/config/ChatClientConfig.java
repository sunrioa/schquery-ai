package cn.ling.config;

import cn.ling.advisor.IntentRecognizerAdvisor;
import cn.ling.advisor.KnowledgeRagAdvisor;
import cn.ling.advisor.McpRagAdvisor;
import cn.ling.advisor.SensitiveFilterAdvisor;
import cn.ling.prompt.Prompts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
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
     * @param knowledgeRagAdvisor RAG问答增强顾问，基于向量数据库检索相关文档
     * @param sensitiveFilterAdvisor 敏感词过滤顾问，过滤用户输入中的敏感内容
     * @param intentRecognizerAdvisor 意图识别顾问，识别用户查询意图
     * @return 配置好的ChatClient聊天客户端
     */
    @Bean("openAiChatClient")
    public ChatClient openAiChatClient(
            OpenAiChatModel openAiChatModel,
            KnowledgeRagAdvisor knowledgeRagAdvisor,
            McpRagAdvisor mcpRagAdvisor,
            SensitiveFilterAdvisor sensitiveFilterAdvisor,
            IntentRecognizerAdvisor intentRecognizerAdvisor
    ) {
        log.info("开始配置OpenAI聊天客户端");
        
        ChatClient chatClient = ChatClient.builder(openAiChatModel)
                .defaultSystem(Prompts.SYSTEM_PROMPT)  // 设置默认系统提示词，定义AI助手角色
                .defaultAdvisors(
                        knowledgeRagAdvisor,        // 知识库RAG增强，顺序: 2（支持knowledge_id过滤）
                        mcpRagAdvisor,              // MCP网络检索增强，顺序: 3
                        intentRecognizerAdvisor,    // 意图识别，顺序: 1
                        sensitiveFilterAdvisor      // 敏感词过滤，顺序: 0
                )
                .build();
        
        log.info("OpenAI聊天客户端配置完成，已启用顾问: RAG增强, 意图识别, 敏感词过滤");
        return chatClient;
    }

    /**
     * 配置会话长期记忆客户端
     * 专门用于压缩长链路历史对话，不启用RAG和顾问，避免引入额外噪声
     *
     * @param openAiChatModel OpenAI聊天模型实例
     * @return 配置好的会话记忆客户端
     */
    @Bean("conversationMemoryChatClient")
    public ChatClient conversationMemoryChatClient(OpenAiChatModel openAiChatModel) {
        log.info("开始配置会话长期记忆客户端");

        ChatClient chatClient = ChatClient.builder(openAiChatModel)
                .defaultSystem(Prompts.CONVERSATION_MEMORY_PROMPT)
                .build();

        log.info("会话长期记忆客户端配置完成");
        return chatClient;
    }

    /**
     * 配置追问改写客户端
     * 专门将依赖上下文的追问改写为独立问题，避免RAG检索丢主体
     *
     * @param openAiChatModel OpenAI聊天模型实例
     * @return 配置好的追问改写客户端
     */
    @Bean("followUpRewriteChatClient")
    public ChatClient followUpRewriteChatClient(OpenAiChatModel openAiChatModel) {
        log.info("开始配置追问改写客户端");

        ChatClient chatClient = ChatClient.builder(openAiChatModel)
                .defaultSystem(Prompts.FOLLOW_UP_REWRITE_PROMPT)
                .build();

        log.info("追问改写客户端配置完成");
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
