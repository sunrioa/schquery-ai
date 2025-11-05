package cn.ling.service;

import cn.ling.domain.Result;
import cn.ling.domain.dto.ChatMessageDTO;
import cn.ling.domain.pojo.ChatMessage;
import cn.ling.domain.vo.ChatMessageVO;
import com.baomidou.mybatisplus.extension.service.IService;
import reactor.core.publisher.Flux;

import java.util.List;

/**
* @author Administrator
* @description 针对表【chat_message(会话中的消息记录表（存储用户、AI、客服的所有对话内容）)】的数据库操作Service
* @createDate 2025-11-05 23:24:44
*/
public interface ChatMessageService extends IService<ChatMessage> {

    Result<List<ChatMessageVO>> getMessage(Long sessionId);

    Flux<String> sendMessage(ChatMessageDTO chatMessageDTO);

    Result<String> deleteMessage(ChatMessageDTO chatMessageDTO);
}

