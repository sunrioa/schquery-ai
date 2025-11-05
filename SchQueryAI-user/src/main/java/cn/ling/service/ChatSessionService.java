package cn.ling.service;

import cn.ling.domain.Result;
import cn.ling.domain.dto.ChatSessionDTO;
import cn.ling.domain.pojo.ChatSession;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ChatSessionService extends IService<ChatSession> {

    Result<String> addSession();

    Result<String> deleteSession(ChatSessionDTO chatSessionDTO);

    Result<String> updateSession(ChatSessionDTO chatSessionDTO);

    Result<String> getSession();
}
