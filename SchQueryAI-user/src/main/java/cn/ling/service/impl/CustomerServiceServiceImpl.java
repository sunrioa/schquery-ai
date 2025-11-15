package cn.ling.service.impl;

import cn.ling.Result;
import cn.ling.dto.CustomerServiceDTO;
import cn.ling.domain.pojo.CustomerServiceMessage;
import cn.ling.domain.pojo.CustomerServiceSession;
import cn.ling.domain.vo.CustomerServiceVO;
import cn.ling.exception.CustomException;
import cn.ling.mapper.CustomerServiceMapper;
import cn.ling.mapper.CustomerServiceSessionMapper;
import cn.ling.service.CustomerServiceService;
import cn.ling.service.ICustomerServiceBridge;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 客服消息服务实现
 */
@Slf4j
@Service
public class CustomerServiceServiceImpl extends ServiceImpl<CustomerServiceMapper, CustomerServiceMessage> 
        implements CustomerServiceService, ICustomerServiceBridge {

    private final CustomerServiceSessionMapper customerServiceSessionMapper;

    public CustomerServiceServiceImpl(CustomerServiceSessionMapper customerServiceSessionMapper) {
        this.customerServiceSessionMapper = customerServiceSessionMapper;
    }

    @Override
    public Result<Long> sendMessage(CustomerServiceDTO dto) {
        try {
            // 创建消息记录
            CustomerServiceMessage message = CustomerServiceMessage.builder()
                    .userId(dto.getUserId())
                    .userName(dto.getUserName())
                    .messageContent(dto.getMessageContent())
                    .senderType(1) // 1-用户
                    .senderId(dto.getUserId())
                    .senderName(dto.getUserName())
                    .readStatus(0) // 未读
                    .topic(dto.getTopic())
                    .createTime(LocalDateTime.now(ZoneId.systemDefault()))
                    .updateTime(LocalDateTime.now(ZoneId.systemDefault()))
                    .build();

            this.save(message);

            // 更新或创建会话
            CustomerServiceSession session = customerServiceSessionMapper.selectByUserId(dto.getUserId());
            if (session == null) {
                // 创建新会话
                session = CustomerServiceSession.builder()
                        .userId(dto.getUserId())
                        .userName(dto.getUserName())
                        .lastMessage(dto.getMessageContent())
                        .unreadCount(1) // 初始未读数为1
                        .sessionStatus(0) // 待处理
                        .createTime(LocalDateTime.now(ZoneId.systemDefault()))
                        .updateTime(LocalDateTime.now(ZoneId.systemDefault()))
                        .build();
                customerServiceSessionMapper.insert(session);
            } else {
                // 更新已有会话
                session.setLastMessage(dto.getMessageContent());
                session.setUnreadCount(session.getUnreadCount() + 1); // 增加未读数
                session.setSessionStatus(0); // 重新激活会话，改为待处理
                session.setUpdateTime(LocalDateTime.now(ZoneId.systemDefault()));
                customerServiceSessionMapper.updateById(session);
            }

            log.info("用户 {} 发送客服消息成功，消息ID: {}", dto.getUserName(), message.getId());
            return Result.success(message.getId(), "消息发送成功");
        } catch (Exception e) {
            log.error("发送客服消息失败", e);
            throw CustomException.error("发送客服消息失败：" + e.getMessage());
        }
    }

    @Override
    public Result<Long> replyMessage(CustomerServiceDTO dto) {
        try {
            // 创建管理员回复消恫
            CustomerServiceMessage message = CustomerServiceMessage.builder()
                    .userId(dto.getUserId())
                    .userName(dto.getUserName())
                    .messageContent(dto.getMessageContent())
                    .senderType(2) // 2-管理员
                    .senderId(dto.getSenderId())
                    .senderName(dto.getSenderName())
                    .readStatus(1) // 管理员发送，自动为已读
                    .topic(dto.getTopic())
                    .createTime(LocalDateTime.now(ZoneId.systemDefault()))
                    .updateTime(LocalDateTime.now(ZoneId.systemDefault()))
                    .build();

            this.save(message);

            // 更新会话的最后消息
            CustomerServiceSession session = customerServiceSessionMapper.selectByUserId(dto.getUserId());
            if (session != null) {
                session.setLastMessage(dto.getMessageContent());
                session.setSessionStatus(1); // 处理中
                session.setAssignedAdminId(dto.getSenderId());
                session.setUpdateTime(LocalDateTime.now(ZoneId.systemDefault()));
                customerServiceSessionMapper.updateById(session);
            }

            log.info("管理员 {} 回复客服消息成功，消息ID: {}", dto.getSenderName(), message.getId());
            
            // 通过WebSocket发送消息给用户
            try {
                // 需要引入CustomerServiceWebSocketHandler的方法
                Class.forName("cn.ling.handler.CustomerServiceWebSocketHandler")
                    .getMethod("sendMessageToUser", String.class, java.util.Map.class)
                    .invoke(null, dto.getUserId().toString(), java.util.Map.ofEntries(
                        java.util.Map.entry("type", "admin_reply"),
                        java.util.Map.entry("fromUserId", dto.getSenderId()),
                        java.util.Map.entry("content", dto.getMessageContent()),
                        java.util.Map.entry("timestamp", System.currentTimeMillis())
                    ));
                log.info("已通过WebSocket发送admin_reply消息给用户 {}", dto.getUserId());
            } catch (Exception e) {
                log.warn("通过WebSocket发送消息失败，可能是用户不在线: {}", e.getMessage());
            }
            
            return Result.success(message.getId(), "消息回复成功");
        } catch (Exception e) {
            log.error("回复客服消息失败", e);
            throw CustomException.error("回复客服消息失败：" + e.getMessage());
        }
    }

    @Override
    public Result<List<CustomerServiceVO>> getMessagesByUserId(Long userId) {
        try {
            List<CustomerServiceMessage> messages = this.baseMapper.selectByUserId(userId);
            List<CustomerServiceVO> result = messages.stream()
                    .map(msg -> CustomerServiceVO.builder()
                            .id(msg.getId())
                            .userId(msg.getUserId())
                            .userName(msg.getUserName())
                            .messageContent(msg.getMessageContent())
                            .senderType(msg.getSenderType())
                            .senderId(msg.getSenderId())
                            .senderName(msg.getSenderName())
                            .readStatus(msg.getReadStatus())
                            .topic(msg.getTopic())
                            .createTime(msg.getCreateTime().toString())
                            .build())
                    .collect(Collectors.toList());

            return Result.success(result);
        } catch (Exception e) {
            log.error("获取客服消息列表失败", e);
            throw CustomException.error("获取客服消息列表失败");
        }
    }

    @Override
    public Result<String> markAsRead(Long userId) {
        try {
            this.baseMapper.markAsRead(userId, 2); // 标记来自管理员(2)的消恫为已读
            // 同时更新session表的unreadCount（回收来自殡理员的已读消恫）
            CustomerServiceSession session = customerServiceSessionMapper.selectByUserId(userId);
            if (session != null) {
                // 计算次未读消恫数（贫元:使用算法）
                // 尚未实现，不过由于我们国有的getStats()不再供markAsRead，所以不需要级联修复
                log.info("会话 {} 已更新", userId);
            }
            log.info("用户 {} 标记消恫已读", userId);
            return Result.success("已标记为已读");
        } catch (Exception e) {
            log.error("标记消恫已读失败", e);
            throw CustomException.error("标记消恫已读失败");
        }
    }

    @Override
    public Result<List<CustomerServiceVO.UserSessionVO>> getPendingSessions() {
        try {
            List<CustomerServiceSession> sessions = customerServiceSessionMapper.selectPendingSessions();
            
            List<CustomerServiceVO.UserSessionVO> result = sessions.stream()
                    .map(session -> {
                        // 获取该用户的消息列表
                        List<CustomerServiceMessage> messages = this.baseMapper.selectByUserId(session.getUserId());
                        
                        List<CustomerServiceVO> messageVOs = messages.stream()
                                .map(msg -> CustomerServiceVO.builder()
                                        .id(msg.getId())
                                        .userId(msg.getUserId())
                                        .userName(msg.getUserName())
                                        .messageContent(msg.getMessageContent())
                                        .senderType(msg.getSenderType())
                                        .senderId(msg.getSenderId())
                                        .senderName(msg.getSenderName())
                                        .readStatus(msg.getReadStatus())
                                        .topic(msg.getTopic())
                                        .createTime(msg.getCreateTime().toString())
                                        .build())
                                .collect(Collectors.toList());

                        // 根据 session_status 转换为 status 字符串
                        String status = "pending";
                        if (session.getSessionStatus() != null) {
                            switch (session.getSessionStatus()) {
                                case 0: status = "pending"; break;
                                case 1: status = "processing"; break;
                                case 2: status = "completed"; break;
                            }
                        }

                        return CustomerServiceVO.UserSessionVO.builder()
                                .id(session.getUserId())  // 使用 userId 作为 id
                                .userId(session.getUserId())
                                .userName(session.getUserName())
                                .lastMessage(session.getLastMessage())
                                .unreadCount(session.getUnreadCount())
                                .lastMessageTime(session.getUpdateTime().toString())
                                .topic(messages.isEmpty() ? "general" : (messages.get(messages.size() - 1).getTopic() != null ? messages.get(messages.size() - 1).getTopic() : "general"))
                                .status(status)
                                .messages(messageVOs)
                                .build();
                    })
                    .collect(Collectors.toList());

            return Result.success(result);
        } catch (Exception e) {
            log.error("获取待处理客服会话失败", e);
            throw CustomException.error("获取待处理客服会话失败");
        }
    }

    @Override
    public Result<CustomerServiceVO.StatsVO> getStats() {
        try {
            Integer pendingCount = customerServiceSessionMapper.getPendingCount();
            // 改为从message表动态计算未读数，而不是使用session表的unreadCount
            // 统计所有status=0(待处理)的会话中，来自用户(senderType=1)且未读(readStatus=0)的消息数
            Integer unreadCount = this.baseMapper.countUnreadMessagesByPendingSessions();
            List<CustomerServiceSession> sessions = customerServiceSessionMapper.selectPendingSessions();

            List<CustomerServiceVO.UserSessionVO> userSessions = sessions.stream()
                    .map(session -> CustomerServiceVO.UserSessionVO.builder()
                            .userId(session.getUserId())
                            .userName(session.getUserName())
                            .lastMessage(session.getLastMessage())
                            .unreadCount(session.getUnreadCount())
                            .lastMessageTime(session.getUpdateTime().toString())
                            .build())
                    .collect(Collectors.toList());

            CustomerServiceVO.StatsVO stats = CustomerServiceVO.StatsVO.builder()
                    .pendingCount(pendingCount != null ? pendingCount : 0)
                    .unreadCount(unreadCount != null ? unreadCount : 0)
                    .completedCount(0)
                    .userSessions(userSessions)
                    .build();

            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取客服统计信息失败", e);
            throw CustomException.error("获取客服统计信息失败");
        }
    }

    @Override
    public Result<String> assignSession(Long userId, Long adminId) {
        try {
            CustomerServiceSession session = customerServiceSessionMapper.selectByUserId(userId);
            if (session == null) {
                throw CustomException.error("会话不存在");
            }

            session.setAssignedAdminId(adminId);
            session.setSessionStatus(1); // 处理中
            session.setUpdateTime(LocalDateTime.now(ZoneId.systemDefault()));
            customerServiceSessionMapper.updateById(session);

            log.info("将用户 {} 的客服会话分配给管理员 {}", userId, adminId);
            return Result.success("分配成功");
        } catch (Exception e) {
            log.error("分配客服会话失败", e);
            throw CustomException.error("分配客服会话失败");
        }
    }

    @Override
    public Result<String> completeSession(Long userId) {
        try {
            CustomerServiceSession session = customerServiceSessionMapper.selectByUserId(userId);
            if (session == null) {
                throw CustomException.error("会话不存在");
            }

            session.setSessionStatus(2); // 已完成
            session.setUpdateTime(LocalDateTime.now(ZoneId.systemDefault()));
            customerServiceSessionMapper.updateById(session);

            log.info("完成用户 {} 的客服会话", userId);
            return Result.success("会话已完成");
        } catch (Exception e) {
            log.error("完成客服会话失败", e);
            throw CustomException.error("完成客服会话失败");
        }
    }

    @Override
    public Result<String> markAsReadByUser(Long userId, Integer senderType) {
        try {
            // 使用MyBatis Plus的update方法标记消恫为已读
            this.update(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaUpdate(CustomerServiceMessage.class)
                    .eq(CustomerServiceMessage::getUserId, userId)
                    .eq(senderType != null && senderType != 0, CustomerServiceMessage::getSenderType, senderType)
                    .set(CustomerServiceMessage::getReadStatus, 1)
            );
            
            // 同时更新session表的unreadCount(指挅定类型的已读消恫)
            CustomerServiceSession session = customerServiceSessionMapper.selectByUserId(userId);
            if (session != null && senderType != null && senderType == 1) {
                // 如果senderType=1(来自用户)，提伛unreadCount
                // 消恫数=该会话中来自用户且未读的消恫数
                Integer unreadCount = this.baseMapper.countUnreadByUserAndType(userId, 1);
                session.setUnreadCount(Math.max(0, unreadCount != null ? unreadCount : 0));
                session.setUpdateTime(LocalDateTime.now(ZoneId.systemDefault()));
                customerServiceSessionMapper.updateById(session);
            }
            
            log.info("用户 {} 的客服消恫已标记为已读，类型: {}", userId, senderType);
            return Result.success("标记成功");
        } catch (Exception e) {
            log.error("标记消恫已读失败", e);
            throw CustomException.error("标记消恫已读失败");
        }
    }
}
