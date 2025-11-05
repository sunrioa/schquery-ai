package cn.ling.service.impl;

import cn.ling.domain.Result;
import cn.ling.domain.dto.ChatMessageDTO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.ling.domain.pojo.ChatMessage;
import cn.ling.service.ChatMessageService;
import cn.ling.mapper.ChatMessageMapper;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
* @author Administrator
* @description 针对表【chat_message(会话中的消息记录表（存储用户、AI、客服的所有对话内容）)】的数据库操作Service实现
* @createDate 2025-11-05 23:24:44
*/
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage>
    implements ChatMessageService{

    @Autowired
    OpenAiChatModel openAiChatModel;

    @Override
    public Result<String> getMessage(Long sessionId) {
        return null;
    }

    @Override
    public Flux<String> sendMessage(ChatMessageDTO chatMessageDTO) {
        return openAiChatModel.stream(chatMessageDTO.getContent());
    }

    @Override
    public Result<String> deleteMessage(ChatMessageDTO chatMessageDTO) {
        return null;
    }
}




