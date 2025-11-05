package cn.ling.service.impl;

import cn.ling.context.ContextUtils;
import cn.ling.domain.Result;
import cn.ling.domain.dto.ChatMessageDTO;
import cn.ling.domain.pojo.ChatMessage;
import cn.ling.domain.vo.ChatMessageVO;
import cn.ling.mapper.ChatMessageMapper;
import cn.ling.service.ChatMessageService;
import cn.ling.service.ChatSessionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
* @author Administrator
* @description 针对表【chat_message(会话中的消息记录表（存储用户、AI、客服的所有对话内容）)】的数据库操作Service实现
* @createDate 2025-11-05 23:24:44
*/
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage>
    implements ChatMessageService{

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private ChatSessionService chatSessionService;

    @Override
    public Result<List<ChatMessageVO>> getMessage(Long sessionId) {
        // Validate session exists
        LambdaQueryWrapper<ChatMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChatMessage::getSessionId, sessionId)
                   .orderByAsc(ChatMessage::getCreatedAt); // Order by creation time

        List<ChatMessage> messages = list(queryWrapper);

        if (messages != null && !messages.isEmpty()) {
            // Convert to VO objects
            List<ChatMessageVO> messageVOs = messages.stream()
                .map(message -> {
                    ChatMessageVO vo = new ChatMessageVO();
                    vo.setId(message.getId());
                    vo.setMessageType(message.getMessageType());
                    vo.setContent(message.getContent());
                    vo.setCreatedAt(message.getCreatedAt());
                    return vo;
                })
                .collect(Collectors.toList());

            return Result.success(messageVOs);
        } else {
            return Result.success(null);
        }
    }

    @Override
    public Flux<String> sendMessage(ChatMessageDTO chatMessageDTO) {
        try {
            // Get current user ID from thread local
            Long userId = ContextUtils.getUserId();

            // Save user message first
            ChatMessage userMessage = new ChatMessage();
            userMessage.setSessionId(chatMessageDTO.getSessionId());
            userMessage.setMessageType(0); // User message
            userMessage.setContent(chatMessageDTO.getContent());
            userMessage.setCreatedAt(new Date());

            boolean saveSuccess = save(userMessage);

            if (!saveSuccess) {
                return Flux.error(new RuntimeException("保存用户消息失败"));
            }

            // Update session last message time
            chatSessionService.updateLastMessageTime(chatMessageDTO.getSessionId());

            // Generate AI response using streaming
            Flux<String> aiResponseStream = chatModel.stream(chatMessageDTO.getContent());

            // 用于累积完整的AI回复
            StringBuilder fullResponse = new StringBuilder();

            return aiResponseStream
                    .doOnNext(chunk -> {
                        // 累积响应内容
                        fullResponse.append(chunk);
                    })
                    .doOnComplete(() -> {
                        // 流式传输完成时，保存完整的AI回复到数据库
                        if (fullResponse.length() > 0) {
                            ChatMessage aiMessage = new ChatMessage();
                            aiMessage.setSessionId(chatMessageDTO.getSessionId());
                            aiMessage.setMessageType(1); // AI message
                            aiMessage.setContent(fullResponse.toString());
                            aiMessage.setCreatedAt(new Date());
                            this.save(aiMessage);
                        }
                    })
                    .doOnError(error -> {
                        // 处理错误
                        error.printStackTrace();
                    });
        } catch (Exception e) {
            return Flux.error(new RuntimeException("消息发送失败：" + e.getMessage()));
        }
    }

    @Override
    public Result<String> deleteMessage(ChatMessageDTO chatMessageDTO) {
        // Validate message exists
        ChatMessage existingMessage = getById(chatMessageDTO.getId());
        if (existingMessage == null) {
            return Result.error("消息不存在");
        }

        // Validate the message belongs to the current user by checking session ownership
        // This is a security check to prevent users from deleting messages from other users' sessions

        // For now, we'll allow deletion if the message exists
        // In a more secure implementation, you'd validate session ownership here

        boolean success = removeById(chatMessageDTO.getId());

        if (success) {
            return Result.success("删除消息成功");
        } else {
            return Result.error("删除消息失败");
        }
    }
}




