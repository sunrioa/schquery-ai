package cn.ling.service.impl;

import cn.ling.domain.Result;
import cn.ling.domain.dto.ChatSessionDTO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.ling.domain.pojo.ChatSession;
import cn.ling.service.ChatSessionService;
import cn.ling.mapper.ChatSessionMapper;
import org.springframework.stereotype.Service;

/**
* @author Administrator
* @description 针对表【chat_session(用户与AI/客服的对话会话表（每个会话对应一个用户的连续对话）)】的数据库操作Service实现
* @createDate 2025-11-05 23:24:44
*/
@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession>
    implements ChatSessionService{

    @Override
    public Result<String> addSession() {
        return null;
    }

    @Override
    public Result<String> deleteSession(ChatSessionDTO chatSessionDTO) {
        return null;
    }

    @Override
    public Result<String> updateSession(ChatSessionDTO chatSessionDTO) {
        return null;
    }

    @Override
    public Result<String> getSession() {
        return null;
    }
}




