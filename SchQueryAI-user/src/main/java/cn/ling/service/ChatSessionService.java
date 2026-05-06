package cn.ling.service;

import cn.ling.Result;
import cn.ling.domain.dto.ChatSessionDTO;
import cn.ling.domain.pojo.ChatSession;
import cn.ling.domain.vo.ChatSessionVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface ChatSessionService extends IService<ChatSession> {

    Result<String> addSession();

    Result<String> deleteSession(ChatSessionDTO chatSessionDTO);

    Result<String> updateSession(ChatSessionDTO chatSessionDTO);

    Result<List<ChatSessionVO>> getSession();

    void updateLastMessageTime(Long sessionId);

    void refreshSessionNameFromFirstQuestion(Long sessionId, String firstQuestion);
}
