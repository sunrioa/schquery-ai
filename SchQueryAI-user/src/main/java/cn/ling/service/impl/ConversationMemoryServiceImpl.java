package cn.ling.service.impl;

import cn.ling.domain.pojo.ChatMessage;
import cn.ling.service.ConversationMemoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 会话长期记忆服务实现
 * 通过分段压缩长链路历史对话，为后续追问保留稳定主体和关键约束
 */
@Slf4j
@Service
public class ConversationMemoryServiceImpl implements ConversationMemoryService {

    private static final int RECENT_HISTORY_MESSAGES = 8;
    private static final int SUMMARY_CHUNK_MESSAGES = 6;
    private static final int SUMMARY_CHUNK_CHARS = 1800;
    private static final int MAX_MEMORY_SUMMARY_CHARS = 600;
    private static final int MAX_SINGLE_MESSAGE_CHARS = 400;
    private static final Pattern CONTEXT_DEPENDENT_QUESTION_PATTERN = Pattern.compile(
            "(这所学校|这个学校|这学校|该校|该学校|这所院校|这所高校|这所大学|这所学院|这个院校|这个高校|这个学院|该学院|该专业|这个专业|这个方向|它|其|上述|上面这所|前面这所|同层次|相比|对比)",
            Pattern.CASE_INSENSITIVE
    );

    private final ConcurrentMap<Long, MemorySnapshot> memoryCache = new ConcurrentHashMap<>();

    @Resource(name = "conversationMemoryChatClient")
    private ChatClient conversationMemoryChatClient;

    @Resource(name = "followUpRewriteChatClient")
    private ChatClient followUpRewriteChatClient;

    @Override
    public String getLongTermMemory(Long sessionId, List<ChatMessage> orderedMessages) {
        List<ChatMessage> validMessages = orderedMessages.stream()
                .filter(this::isMemoryMessage)
                .collect(Collectors.toList());

        if (validMessages.size() <= RECENT_HISTORY_MESSAGES) {
            clearMemory(sessionId);
            return "";
        }

        List<ChatMessage> olderMessages = validMessages.subList(0, validMessages.size() - RECENT_HISTORY_MESSAGES);
        String signature = buildSignature(olderMessages);
        MemorySnapshot cachedSnapshot = memoryCache.get(sessionId);
        if (cachedSnapshot != null && Objects.equals(cachedSnapshot.signature(), signature)) {
            return cachedSnapshot.summary();
        }

        String summary = summarizeOlderMessages(sessionId, olderMessages);
        if (!StringUtils.hasText(summary)) {
            clearMemory(sessionId);
            return "";
        }

        memoryCache.put(sessionId, new MemorySnapshot(signature, summary));
        return summary;
    }

    @Override
    public String rewriteQuestion(Long sessionId, String longTermMemory, String recentHistory, String currentQuestion) {
        if (!StringUtils.hasText(currentQuestion)) {
            return currentQuestion;
        }

        if (!needsRewrite(currentQuestion)) {
            return currentQuestion.trim();
        }

        String prompt = """
                会话长期记忆：
                %s

                最近对话历史：
                %s

                当前问题：
                %s

                请直接输出改写后的独立问题：
                """.formatted(
                StringUtils.hasText(longTermMemory) ? longTermMemory : "（暂无）",
                StringUtils.hasText(recentHistory) ? recentHistory : "（暂无）",
                currentQuestion
        );

        try {
            String rewrittenQuestion = followUpRewriteChatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (!StringUtils.hasText(rewrittenQuestion)) {
                return currentQuestion.trim();
            }

            String normalizedQuestion = normalizeSummary(rewrittenQuestion);
            log.info("会话 {} 追问改写完成，原问题: {}, 改写后: {}", sessionId, currentQuestion, normalizedQuestion);
            return normalizedQuestion;
        } catch (Exception e) {
            log.warn("会话 {} 追问改写失败，将回退为原问题: {}", sessionId, e.getMessage());
            return currentQuestion.trim();
        }
    }

    @Override
    public void clearMemory(Long sessionId) {
        if (sessionId != null) {
            memoryCache.remove(sessionId);
        }
    }

    private boolean isMemoryMessage(ChatMessage message) {
        return message != null
                && message.getMessageType() != null
                && message.getMessageType() != -1
                && StringUtils.hasText(message.getContent());
    }

    private boolean needsRewrite(String currentQuestion) {
        return CONTEXT_DEPENDENT_QUESTION_PATTERN.matcher(currentQuestion.trim()).find();
    }

    private String summarizeOlderMessages(Long sessionId, List<ChatMessage> olderMessages) {
        String rollingSummary = "";
        List<String> chunkLines = new ArrayList<>();
        int chunkChars = 0;

        for (ChatMessage message : olderMessages) {
            String line = formatMessageLine(message);
            if (!StringUtils.hasText(line)) {
                continue;
            }

            boolean shouldFlushChunk = !chunkLines.isEmpty()
                    && (chunkLines.size() >= SUMMARY_CHUNK_MESSAGES || chunkChars + line.length() > SUMMARY_CHUNK_CHARS);
            if (shouldFlushChunk) {
                rollingSummary = updateSummary(sessionId, rollingSummary, chunkLines);
                chunkLines = new ArrayList<>();
                chunkChars = 0;
            }

            chunkLines.add(line);
            chunkChars += line.length();
        }

        if (!chunkLines.isEmpty()) {
            rollingSummary = updateSummary(sessionId, rollingSummary, chunkLines);
        }

        return truncateSummary(rollingSummary);
    }

    private String updateSummary(Long sessionId, String previousSummary, List<String> chunkLines) {
        String transcript = String.join("\n", chunkLines);
        String prompt = """
                已有摘要：
                %s

                新增对话片段：
                %s

                请直接输出更新后的长期记忆摘要：
                """.formatted(StringUtils.hasText(previousSummary) ? previousSummary : "（暂无）", transcript);

        try {
            String summary = conversationMemoryChatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (!StringUtils.hasText(summary)) {
                return previousSummary;
            }
            return truncateSummary(normalizeSummary(summary));
        } catch (Exception e) {
            log.warn("更新会话 {} 的长期记忆摘要失败，将回退到上一版摘要: {}", sessionId, e.getMessage());
            return previousSummary;
        }
    }

    private String formatMessageLine(ChatMessage message) {
        String content = normalizeText(message.getContent());
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String role = message.getMessageType() != null && message.getMessageType() == 1 ? "助手" : "用户";
        return role + "：" + truncateText(content, MAX_SINGLE_MESSAGE_CHARS);
    }

    private String buildSignature(List<ChatMessage> messages) {
        long hash = 1L;
        for (ChatMessage message : messages) {
            hash = 31 * hash + Objects.hash(
                    message.getId(),
                    message.getMessageType(),
                    normalizeText(message.getContent())
            );
        }
        return messages.size() + ":" + hash;
    }

    private String normalizeSummary(String summary) {
        return summary.replace("\r", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private String truncateSummary(String summary) {
        return truncateText(normalizeSummary(summary), MAX_MEMORY_SUMMARY_CHARS);
    }

    private String truncateText(String text, int maxLength) {
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private record MemorySnapshot(String signature, String summary) {
    }
}
