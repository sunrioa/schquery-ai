package cn.ling.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客服消息 DTO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CustomerServiceDTO {
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
     * 用户头像URL（兼容字段，用于用户发送消息时）
     */
    private String userAvatar;

    /**
     * 消息状态 (1-已读, 0-未读)
     */
    private Integer readStatus;

    /**
     * 话题
     */
    private String topic;

    /**
     * 创建时间戳
     */
    private Long createTime;
}
