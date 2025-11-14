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
 * 客服会话表
 * @TableName customer_service_session
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "customer_service_session")
public class CustomerServiceSession {
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
     * 最后一条消息
     */
    private String lastMessage;

    /**
     * 未读消息数
     */
    private Integer unreadCount;

    /**
     * 会话状态 (0-待处理, 1-处理中, 2-已完成)
     */
    private Integer sessionStatus;

    /**
     * 分配给的管理员ID
     */
    private Long assignedAdminId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
