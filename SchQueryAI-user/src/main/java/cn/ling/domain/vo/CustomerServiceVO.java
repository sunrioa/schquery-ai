package cn.ling.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 客服消息 VO - 用于页面展示
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CustomerServiceVO {
    /**
     * 消息ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 消息内容
     */
    private String messageContent;

    /**
     * 发送者类型 (1-用户, 2-管理员)
     */
    private Integer senderType;

    /**
     * 发送者ID
     */
    private Long senderId;

    /**
     * 发送者名称
     */
    private String senderName;

    /**
     * 发送者头像URL
     */
    private String senderAvatar;

    /**
     * 消息状态 (1-已读, 0-未读)
     */
    private Integer readStatus;

    /**
     * 话题
     */
    private String topic;

    /**
     * 创建时间 (格式化后的字符串)
     */
    private String createTime;

    /**
     * 用户会话列表 VO - 显示所有待处理客服请求的用户
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserSessionVO {
        /**
         * 会话ID（用户ID）
         */
        private Long id;

        /**
         * 用户ID
         */
        private Long userId;

        /**
         * 用户名
         */
        private String userName;

        /**
         * 最后一条消息
         */
        private String lastMessage;

        /**
         * 未读消息数
         */
        private Integer unreadCount;

        /**
         * 最后消息时间
         */
        private String lastMessageTime;

        /**
         * 用户头像
         */
        private Long userAvatar;

        /**
         * 话题
         */
        private String topic;

        /**
         * 会话状态
         */
        private String status;

        /**
         * 消息列表
         */
        private List<CustomerServiceVO> messages;
    }

    /**
     * 统计信息 VO
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StatsVO {
        /**
         * 待处理客服请求总数
         */
        private Integer pendingCount;

        /**
         * 未读消息总数
         */
        private Integer unreadCount;

        /**
         * 已完成客服会话数
         */
        private Integer completedCount;

        /**
         * 用户会话列表
         */
        private List<UserSessionVO> userSessions;
    }
}
