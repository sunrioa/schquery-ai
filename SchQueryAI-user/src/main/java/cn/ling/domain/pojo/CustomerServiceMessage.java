package cn.ling.domain.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 客服消息表
 * @TableName customer_service_message
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "customer_service_message")
public class CustomerServiceMessage {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
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
     * 发送者ID (用户ID或管理员ID)
     */
    private Long senderId;

    /**
     * 发送者名称
     */
    private String senderName;

    /**
     * 消息状态 (1-已读, 0-未读)
     */
    private Integer readStatus;

    /**
     * 回话话题/类别
     */
    private String topic;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
