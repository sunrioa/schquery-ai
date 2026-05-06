package cn.ling.handler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import cn.ling.service.ICustomerServiceBridge;
import cn.ling.dto.CustomerServiceDTO;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客服 WebSocket 处理器
 * 用于处理用户和管理员之间的实时通信
 */
@Slf4j
@Component
public class CustomerServiceWebSocketHandler extends TextWebSocketHandler {

    /**
     * 存储用户 WebSocket 连接：userId -> WebSocketSession
     */
    private static final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    /**
     * 存储管理员 WebSocket 连接：adminId -> WebSocketSession
     */
    private static final Map<String, WebSocketSession> adminSessions = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private ICustomerServiceBridge customerServiceBridge;

    /**
     * 用户连接成功后的处理
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = getUserIdFromSession(session);
        String userType = getUserTypeFromSession(session);

        if ("user".equals(userType)) {
            userSessions.put(userId, session);
            log.info("用户 {} WebSocket 连接成功", userId);
        } else if ("admin".equals(userType)) {
            adminSessions.put(userId, session);
            log.info("管理员 {} WebSocket 连接成功", userId);
            // 通知所有管理员有新连接
            broadcastToAdmins(buildMessage("admin_connected", userId, "有新的客服会话"));
        }
    }

    /**
     * 处理接收到的消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            String payload = message.getPayload();
        //    log.info("收到WebSocket原始消息: {}", payload);

            // 兼容前端心跳：可能发送纯文本 ping 或 {type:"ping"}
            if (payload != null && payload.trim().equalsIgnoreCase("ping")) {
                Map<String, Object> pong = new HashMap<>();
                pong.put("type", "pong");
                pong.put("timestamp", System.currentTimeMillis());
                synchronized (session) {
                    session.sendMessage(new TextMessage(JSON.toJSONString(pong)));
                }
                return;
            }
            
            Map<String, Object> msgData = JSON.parseObject(payload, Map.class);
            log.info("解析后的消息对象: {}", msgData);

            // 安全地转换字段为String类型
            String messageType = convertToString(msgData.get("type"));
            String fromUserId = convertToString(msgData.get("fromUserId"));
            String fromType = convertToString(msgData.get("fromType")); // user 或 admin
            String toUserId = convertToString(msgData.get("toUserId"));
            String content = convertToString(msgData.get("content"));
            String userName = convertToString(msgData.get("userName")); // 用户名
            String topic = convertToString(msgData.get("topic")); // 咨询主题

         //   log.info("收到消息 - 类型: {}, 来自: {} ({}), 用户名: {}, 主题: {}, 内容: {}", messageType, fromUserId, fromType, userName, topic, content);

            if ("ping".equalsIgnoreCase(messageType)) {
                Map<String, Object> pong = new HashMap<>();
                pong.put("type", "pong");
                pong.put("timestamp", System.currentTimeMillis());
                synchronized (session) {
                    session.sendMessage(new TextMessage(JSON.toJSONString(pong)));
                }
                return;
            }

            if ("user_message".equals(messageType)) {
                // 用户发送消息给管理员
                handleUserMessage(fromUserId, userName, toUserId, content, topic);
            } else if ("admin_message".equals(messageType)) {
                // 管理员回复用户
                handleAdminMessage(fromUserId, toUserId, content);
            } else if ("admin_notification".equals(messageType)) {
                // 通知所有管理员有新消恫
                broadcastToAdmins(buildMessage("new_user_message", fromUserId, content));
            } else if ("admin_viewing".equals(messageType)) {
                // 管理员正在查看某个用户的消恫
                handleAdminViewing(fromUserId, toUserId);
            } else {
                log.warn("未知的消息类型: {}", messageType);
            }
        } catch (Exception e) {
            log.error("处理 WebSocket 消息失败", e);
            sendErrorMessage(session, "消息处理失败");
        }
    }

    /**
     * 安全地将任意类型转换为String
     */
    private String convertToString(Object obj) {
        if (obj == null) {
            return null;
        }
        return obj.toString();
    }

    /**
     * 处理用户消息
     */
    private void handleUserMessage(String userId, String userName, String toUserId, String content, String topic) {
        try {
            // 1. 保存消息到数据库
            if (customerServiceBridge != null) {
                CustomerServiceDTO dto = CustomerServiceDTO.builder()
                        .userId(Long.parseLong(userId))
                        .userName(userName != null ? userName : "用户" + userId)
                        .messageContent(content)
                        .senderType(1) // 1-用户
                        .topic(topic != null ? topic : "general")
                        .build();
                
                customerServiceBridge.sendMessage(dto);
                log.info("用户消息已保存到数据库: 用户ID={}, 用户名={}, 主题={}", userId, userName, topic);
            } else {
                log.warn("客服服务未注入，消息仅转发不保存");
            }
        } catch (Exception e) {
            log.error("保存用户消息失败", e);
        }
        
        // 2. 构造管理员接收消息的通知
        Map<String, Object> notifyMsg = new HashMap<>();
        notifyMsg.put("type", "user_message_received");
        notifyMsg.put("fromUserId", userId);
        notifyMsg.put("userName", userName != null ? userName : userId);
        notifyMsg.put("content", content);
        notifyMsg.put("topic", topic != null ? topic : "general");
        notifyMsg.put("timestamp", System.currentTimeMillis());
        
        // 3. 将消息发送给所有在线的管理员
        broadcastToAdmins(notifyMsg);

        log.info("用户 {} ({}) 的消息已发送给所有管理员", userId, userName);
    }

    /**
     * 处理管理员回复
     */
    private void handleAdminMessage(String adminId, String userId, String content) {
        // 1. 将消息发送给该用户（如果在线）
        WebSocketSession userSession = userSessions.get(userId);
        if (userSession != null && userSession.isOpen()) {
            try {
                Map<String, Object> msgData = new HashMap<>();
                msgData.put("type", "admin_reply");
                msgData.put("fromUserId", adminId);
                msgData.put("content", content);
                msgData.put("timestamp", System.currentTimeMillis());
                
                // 使用synchronized确保线程安全
                synchronized (userSession) {
                    userSession.sendMessage(new TextMessage(JSON.toJSONString(msgData)));
                }
                log.info("管理员 {} 的回复已发送给用户 {}", adminId, userId);
            } catch (IOException e) {
                log.error("发送消息给用户失败", e);
            }
        } else {
            log.warn("用户 {} 不在线，消息已保存", userId);
        }

        // 2. 通知其他管理员消息已回复
        Map<String, Object> notifyMsg = new HashMap<>();
        notifyMsg.put("type", "admin_replied");
        notifyMsg.put("userId", userId);
        notifyMsg.put("content", content);
        notifyMsg.put("timestamp", System.currentTimeMillis());
        
        TextMessage textMessage = new TextMessage(JSON.toJSONString(notifyMsg));
        for (Map.Entry<String, WebSocketSession> entry : adminSessions.entrySet()) {
            if (!entry.getKey().equals(adminId) && entry.getValue().isOpen()) {
                try {
                    // 使用synchronized确保线程安全
                    synchronized (entry.getValue()) {
                        entry.getValue().sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    log.error("发送通知给管理员失败", e);
                }
            }
        }
    }

    /**
     * 向所有管理员广播消息
     */
    private void broadcastToAdmins(Map<String, Object> message) {
        String msgStr = JSON.toJSONString(message);
        TextMessage textMessage = new TextMessage(msgStr);
        
        for (WebSocketSession session : adminSessions.values()) {
            if (session != null && session.isOpen()) {
                try {
                    // 使用synchronized确保同一时间只有一个线程向同一个session发送消息
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    log.error("向管理员广播消息失败", e);
                }
            }
        }
    }

    /**
     * 发送错误消息
     */
    private void sendErrorMessage(WebSocketSession session, String error) {
        try {
            Map<String, Object> errorMsg = buildMessage("error", "", error);
            synchronized (session) {
                session.sendMessage(new TextMessage(JSON.toJSONString(errorMsg)));
            }
        } catch (IOException e) {
            log.error("发送错误消息失败", e);
        }
    }

    /**
     * 构建消息对象
     */
    private Map<String, Object> buildMessage(String type, String fromUserId, String content) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", type);
        msg.put("fromUserId", fromUserId);
        msg.put("content", content);
        msg.put("timestamp", System.currentTimeMillis());
        return msg;
    }

    /**
     * 从 Session 中获取用户ID
     */
    private String getUserIdFromSession(WebSocketSession session) {
        String query = session.getUri().getQuery();
        if (query != null && query.contains("userId=")) {
            return query.split("userId=")[1].split("&")[0];
        }
        return "unknown";
    }

    /**
     * 从 Session 中获取用户类型
     */
    private String getUserTypeFromSession(WebSocketSession session) {
        String query = session.getUri().getQuery();
        if (query != null && query.contains("userType=")) {
            return query.split("userType=")[1].split("&")[0];
        }
        return "user";
    }

    /**
     * 连接关闭时的处理
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = getUserIdFromSession(session);
        String userType = getUserTypeFromSession(session);

        if ("user".equals(userType)) {
            userSessions.remove(userId);
            log.info("用户 {} WebSocket 连接已关闭", userId);
        } else if ("admin".equals(userType)) {
            adminSessions.remove(userId);
            log.info("管理员 {} WebSocket 连接已关闭", userId);
        }
    }

    /**
     * 获取当前在线用户数量
     */
    public static int getOnlineUserCount() {
        return userSessions.size();
    }

    /**
     * 获取当前在线管理员数量
     */
    public static int getOnlineAdminCount() {
        return adminSessions.size();
    }

    /**
     * 检查用户是否在线
     */
    public static boolean isUserOnline(String userId) {
        return userSessions.containsKey(userId);
    }

    /**
     * 检查管理员是否在线
     */
    public static boolean isAdminOnline(String adminId) {
        return adminSessions.containsKey(adminId);
    }

    /**
     * 发送消恫给指定的用户
     */
    public static void sendMessageToUser(String userId, Map<String, Object> message) {
        WebSocketSession userSession = userSessions.get(userId);
        if (userSession != null && userSession.isOpen()) {
            try {
                synchronized (userSession) {
                    userSession.sendMessage(new TextMessage(JSON.toJSONString(message)));
                }
                log.info("消恫已发送给用户 {}", userId);
            } catch (IOException e) {
                log.error("发送消恫给用户 {} 失败", userId, e);
            }
        } else {
            log.warn("用户 {} 不在线", userId);
        }
    }

    /**
     * 处理管理员查看用户消恫
     */
    private void handleAdminViewing(String adminId, String userId) {
        WebSocketSession userSession = userSessions.get(userId);
        if (userSession != null && userSession.isOpen()) {
            try {
                Map<String, Object> viewingMsg = new HashMap<>();
                viewingMsg.put("type", "admin_viewing");
                viewingMsg.put("adminId", adminId);
                viewingMsg.put("timestamp", System.currentTimeMillis());
                synchronized (userSession) {
                    userSession.sendMessage(new TextMessage(JSON.toJSONString(viewingMsg)));
                }
                log.info("已告诉用户 {} 管理员 {} 正在查看", userId, adminId);
            } catch (IOException e) {
                log.error("发送查看事件失败", e);
            }
        } else {
            log.warn("用户 {} 不在线", userId);
        }
    }
}
