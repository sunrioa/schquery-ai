package cn.ling.service.impl;

import cn.ling.exception.CustomException;
import cn.ling.utils.ContextUtils;
import cn.ling.Result;
import cn.ling.domain.dto.ChatSessionDTO;
import cn.ling.domain.pojo.ChatSession;
import cn.ling.domain.vo.ChatSessionVO;
import cn.ling.mapper.ChatSessionMapper;
import cn.ling.service.ChatSessionService;
import cn.ling.service.SysConfigService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 聊天会话服务实现类
 * 管理用户与AI/客服的对话会话，包括会话的创建、更新、删除和查询功能
 * 每个会话对应一个用户的连续对话记录
 */
@Slf4j // 启用SLF4J日志功能
@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession>
    implements ChatSessionService{

    private static final String KEY_DEFAULT_PRESET_ID = "chat.preset.defaultId";

    @Resource
    private SysConfigService sysConfigService;

    /**
     * 创建新的聊天会话
     * 为当前登录用户创建一个新的对话会话，默认会话名称为"新会话"
     *
     * @return 包含新创建会话ID的结果对象
     */
    @Override
    public Result<String> addSession() {
        log.info("开始创建新的聊天会话");

        try {
            // 从上下文中获取当前用户ID
            Long userId = ContextUtils.getUserId();
            log.debug("获取到当前用户ID: {}", userId);

            // 创建新的聊天会话对象
            Date currentTime = new Date();
            ChatSession chatSession = new ChatSession();
            chatSession.setUserId(userId);
            chatSession.setPresetId(parseLong(sysConfigService.getConfigValue(KEY_DEFAULT_PRESET_ID)));
            chatSession.setSessionName("新会话");
            chatSession.setStatus(1); // 活跃状态
            chatSession.setCreatedAt(currentTime);
            chatSession.setUpdatedAt(currentTime);
            chatSession.setLastMessageAt(currentTime);

            // 保存到数据库
            log.debug("开始保存会话到数据库");
            boolean success = save(chatSession);

            if (success) {
                log.info("聊天会话创建成功，会话ID: {}, 用户ID: {}", chatSession.getId(), userId);
                return Result.success(chatSession.getId().toString());
            } else {
                log.error("聊天会话创建失败，用户ID: {}", userId);
                return Result.error("创建会话失败");
            }
        } catch (Exception e) {
            log.error("创建聊天会话时发生异常: {}", e.getMessage(), e);
            throw CustomException.error("创建会话失败：" + e.getMessage());
        }
    }

    /**
     * 删除聊天会话
     * 执行软删除操作，将会话状态设置为0，保留历史记录但不显示
     *
     * @param chatSessionDTO 包含会话ID的数据传输对象
     * @return 删除操作的结果对象
     */
    @Override
    public Result<String> deleteSession(ChatSessionDTO chatSessionDTO) {
        log.info("开始删除聊天会话，会话ID: {}", chatSessionDTO.getId());

        try {
            // 从上下文中获取当前用户ID
            Long userId = ContextUtils.getUserId();
            log.debug("获取到当前用户ID: {}", userId);

            // 验证会话是否存在且属于当前用户
            log.debug("验证会话存在性和权限");
            ChatSession existingSession = getById(chatSessionDTO.getId());
            if (existingSession == null) {
                log.warn("会话不存在，会话ID: {}", chatSessionDTO.getId());
                return Result.error("会话不存在");
            }

            if (!existingSession.getUserId().equals(userId)) {
                log.warn("用户无权限删除此会话，用户ID: {}, 会话所有者ID: {}", userId, existingSession.getUserId());
                return Result.error("无权限删除此会话");
            }

            // 软删除：设置状态为0
            log.debug("执行软删除操作");
            existingSession.setStatus(0); // 非活跃状态
            existingSession.setUpdatedAt(new Date());

            boolean success = updateById(existingSession);

            if (success) {
                log.info("聊天会话删除成功，会话ID: {}, 用户ID: {}", chatSessionDTO.getId(), userId);
                return Result.success();
            } else {
                log.error("聊天会话删除失败，会话ID: {}, 用户ID: {}", chatSessionDTO.getId(), userId);
                return Result.error("删除会话失败");
            }
        } catch (Exception e) {
            log.error("删除聊天会话时发生异常，会话ID: {}, 异常信息: {}", chatSessionDTO.getId(), e.getMessage(), e);
            throw CustomException.error("删除会话失败：" + e.getMessage());
        }
    }

    /**
     * 更新聊天会话
     * 允许用户修改会话名称，会话的其他属性保持不变
     *
     * @param chatSessionDTO 包含会话ID和新会话名称的数据传输对象
     * @return 更新操作的结果对象
     */
    @Override
    public Result<String> updateSession(ChatSessionDTO chatSessionDTO) {
        log.info("开始更新聊天会话，会话ID: {}, 新会话名称: {}", chatSessionDTO.getId(), chatSessionDTO.getSessionName());

        try {
            // 从上下文中获取当前用户ID
            Long userId = ContextUtils.getUserId();
            log.debug("获取到当前用户ID: {}", userId);

            // 验证会话是否存在且属于当前用户
            log.debug("验证会话存在性和权限");
            ChatSession existingSession = getById(chatSessionDTO.getId());
            if (existingSession == null) {
                log.warn("会话不存在，会话ID: {}", chatSessionDTO.getId());
                throw CustomException.error("会话不存在");
            }

            if (!existingSession.getUserId().equals(userId)) {
                log.warn("用户无权限修改此会话，用户ID: {}, 会话所有者ID: {}", userId, existingSession.getUserId());
                throw CustomException.error("无权限修改此会话");
            }

            boolean changed = false;

            if (StringUtils.hasText(chatSessionDTO.getSessionName())) {
                String newName = chatSessionDTO.getSessionName().trim();
                log.debug("更新会话名称从 '{}' 到 '{}'", existingSession.getSessionName(), newName);
                existingSession.setSessionName(newName);
                changed = true;
            }

            if (chatSessionDTO.getPresetId() != null) {
                existingSession.setPresetId(chatSessionDTO.getPresetId());
                changed = true;
            }

            if (!changed) {
                return Result.success();
            }
            existingSession.setUpdatedAt(new Date());

            boolean success = updateById(existingSession);

            if (success) {
                log.info("聊天会话更新成功，会话ID: {}, 用户ID: {}, 新名称: {}",
                    chatSessionDTO.getId(), userId, chatSessionDTO.getSessionName());
                return Result.success();
            } else {
                log.error("聊天会话更新失败，会话ID: {}, 用户ID: {}", chatSessionDTO.getId(), userId);
                return Result.error("更新会话失败");
            }
        } catch (Exception e) {
            log.error("更新聊天会话时发生异常，会话ID: {}, 异常信息: {}", chatSessionDTO.getId(), e.getMessage(), e);
            throw CustomException.error("更新会话失败：" + e.getMessage());
        }
    }

    /**
     * 获取当前用户的所有聊天会话
     * 查询当前用户的所有活跃会话，按最后消息时间降序排列
     *
     * @return 包含会话列表的结果对象，如果没有会话则返回null
     */
    @Override
    public Result<List<ChatSessionVO>> getSession() {
        log.info("开始获取当前用户的聊天会话列表");

        try {
            // 从上下文中获取当前用户ID
            Long userId = ContextUtils.getUserId();
            log.debug("获取到当前用户ID: {}", userId);

            // 查询当前用户的所有活跃会话
            log.debug("查询用户 {} 的活跃会话列表", userId);
            List<ChatSession> sessions = lambdaQuery()
                    .eq(ChatSession::getUserId, userId)
                    .eq(ChatSession::getStatus, 1) // 仅活跃状态
                    .orderByDesc(ChatSession::getLastMessageAt).list();

            if (sessions != null && !sessions.isEmpty()) {
                log.info("查询到 {} 个活跃会话", sessions.size());

                // 转换为VO对象
                log.debug("开始转换为VO对象");
                List<ChatSessionVO> sessionVOs = sessions.stream()
                    .map(session -> {
                        ChatSessionVO vo = new ChatSessionVO();
                        vo.setId(session.getId());
                        vo.setSessionName(session.getSessionName());
                        vo.setPresetId(session.getPresetId());
                        vo.setLastMessageAt(session.getLastMessageAt());
                        return vo;
                    })
                    .collect(Collectors.toList());

                log.info("成功获取用户 {} 的 {} 个会话", userId, sessionVOs.size());
                return Result.success(sessionVOs);
            } else {
                log.info("用户 {} 没有活跃的聊天会话", userId);
                return Result.success(null);
            }
        } catch (Exception e) {
            log.error("获取聊天会话列表时发生异常: {}", e.getMessage(), e);
            throw CustomException.error("获取会话列表失败：" + e.getMessage());
        }
    }

    /**
     * 更新会话的最后消息时间
     * 在有新消息发送时调用此方法，用于保持会话列表的实时排序
     *
     * @param sessionId 会话ID
     */
    @Override
    public void updateLastMessageTime(Long sessionId) {
        log.debug("开始更新会话 {} 的最后消息时间", sessionId);

        try {
            ChatSession session = getById(sessionId);
            if (session != null) {
                Date currentTime = new Date();
                log.debug("更新会话 {} 的最后消息时间为 {}", sessionId, currentTime);

                session.setLastMessageAt(currentTime);
                session.setUpdatedAt(currentTime);

                boolean success = updateById(session);
                if (success) {
                    log.debug("会话 {} 的最后消息时间更新成功", sessionId);
                } else {
                    log.warn("会话 {} 的最后消息时间更新失败", sessionId);
                }
            } else {
                log.warn("尝试更新不存在的会话 {} 的最后消息时间", sessionId);
            }
        } catch (Exception e) {
            log.error("更新会话 {} 最后消息时间时发生异常: {}", sessionId, e.getMessage(), e);
        }
    }

    private static Long parseLong(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (Exception ignored) {
            return null;
        }
    }
}




