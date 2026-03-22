package cn.ling.service;

import cn.ling.domain.pojo.ChatMessage;

import java.util.List;

/**
 * 会话长期记忆服务
 * 负责从长链路历史对话中提炼可复用的长期上下文
 */
public interface ConversationMemoryService {

    /**
     * 根据当前会话历史生成或读取长期记忆摘要
     *
     * @param sessionId 会话ID
     * @param orderedMessages 按时间正序排列的会话消息
     * @return 长期记忆摘要
     */
    String getLongTermMemory(Long sessionId, List<ChatMessage> orderedMessages);

    /**
     * 将依赖上下文的追问改写为独立问题
     *
     * @param sessionId 会话ID
     * @param longTermMemory 长期记忆摘要
     * @param recentHistory 最近对话历史
     * @param currentQuestion 当前问题
     * @return 改写后的独立问题
     */
    String rewriteQuestion(Long sessionId, String longTermMemory, String recentHistory, String currentQuestion);

    /**
     * 清除指定会话的长期记忆缓存
     *
     * @param sessionId 会话ID
     */
    void clearMemory(Long sessionId);
}
