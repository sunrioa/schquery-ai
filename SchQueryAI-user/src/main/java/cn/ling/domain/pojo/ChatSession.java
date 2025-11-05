package cn.ling.domain.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 用户与AI/客服的对话会话表（每个会话对应一个用户的连续对话）
 * @TableName chat_session
 */
@TableName(value ="chat_session")
@Data
public class ChatSession {
    /**
     * 会话主键ID（唯一标识）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID（关联user表id，标识会话归属）
     */
    private Long userId;

    /**
     * 会话名称（用户可自定义，默认"新会话"）
     */
    private String sessionName;

    /**
     * 会话状态（1-活跃，0-已结束）
     */
    private Integer status;

    /**
     * 最后一条消息的发送时间（用于会话排序）
     */
    private Date lastMessageAt;

    /**
     * 会话创建时间
     */
    private Date createdAt;

    /**
     * 会话信息最后更新时间
     */
    private Date updatedAt;
}