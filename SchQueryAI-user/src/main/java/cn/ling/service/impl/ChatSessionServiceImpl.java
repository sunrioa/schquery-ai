package cn.ling.service.impl;

import cn.ling.context.ContextUtils;
import cn.ling.domain.Result;
import cn.ling.domain.dto.ChatSessionDTO;
import cn.ling.domain.pojo.ChatSession;
import cn.ling.domain.vo.ChatSessionVO;
import cn.ling.mapper.ChatSessionMapper;
import cn.ling.service.ChatSessionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

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
        // Get current user ID from context
        Long userId = ContextUtils.getUserId();

        // Create new chat session
        ChatSession chatSession = new ChatSession();
        chatSession.setUserId(userId);
        chatSession.setSessionName("新会话");
        chatSession.setStatus(1); // Active status
        chatSession.setCreatedAt(new Date());
        chatSession.setUpdatedAt(new Date());
        chatSession.setLastMessageAt(new Date());

        // Save to database
        boolean success = save(chatSession);

        if (success) {
            return Result.success(chatSession.getId().toString());
        } else {
            return Result.error("创建会话失败");
        }
    }

    @Override
    public Result<String> deleteSession(ChatSessionDTO chatSessionDTO) {
        // Get current user ID from context
        Long userId = ContextUtils.getUserId();

        // Validate session exists and belongs to current user
        ChatSession existingSession = getById(chatSessionDTO.getId());
        if (existingSession == null) {
            return Result.error("会话不存在");
        }

        if (!existingSession.getUserId().equals(userId)) {
            return Result.error("无权限删除此会话");
        }

        // Soft delete by setting status to 0
        existingSession.setStatus(0);
        existingSession.setUpdatedAt(new Date());

        boolean success = updateById(existingSession);

        if (success) {
            return Result.success();
        } else {
            return Result.error("删除会话失败");
        }
    }

    @Override
    public Result<String> updateSession(ChatSessionDTO chatSessionDTO) {
        // Get current user ID from context
        Long userId = ContextUtils.getUserId();

        // Validate session exists and belongs to current user
        ChatSession existingSession = getById(chatSessionDTO.getId());
        if (existingSession == null) {
            return Result.error("会话不存在");
        }

        if (!existingSession.getUserId().equals(userId)) {
            return Result.error("无权限修改此会话");
        }

        // Update session name
        existingSession.setSessionName(chatSessionDTO.getSessionName());
        existingSession.setUpdatedAt(new Date());

        boolean success = updateById(existingSession);

        if (success) {
            return Result.success();
        } else {
            return Result.error("更新会话失败");
        }
    }

    @Override
    public Result<List<ChatSessionVO>> getSession() {
        // Get current user ID from context
        Long userId = ContextUtils.getUserId();

        // Query all active sessions for current user
        LambdaQueryWrapper<ChatSession> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChatSession::getUserId, userId)
                   .eq(ChatSession::getStatus, 1) // Active status only
                   .orderByDesc(ChatSession::getLastMessageAt); // Order by last message time

        List<ChatSession> sessions = list(queryWrapper);

        if (sessions != null && !sessions.isEmpty()) {
            // Convert to VO objects
            List<ChatSessionVO> sessionVOs = sessions.stream()
                .map(session -> {
                    ChatSessionVO vo = new ChatSessionVO();
                    vo.setId(session.getId());
                    vo.setSessionName(session.getSessionName());
                    vo.setLastMessageAt(session.getLastMessageAt());
                    return vo;
                })
                .collect(Collectors.toList());

            return Result.success(sessionVOs);
        } else {
            return Result.success(null);
        }
    }

    @Override
    public void updateLastMessageTime(Long sessionId) {
        ChatSession session = getById(sessionId);
        if (session != null) {
            session.setLastMessageAt(new Date());
            session.setUpdatedAt(new Date());
            updateById(session);
        }
    }
}




