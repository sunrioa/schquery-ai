package cn.ling.domain.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 会话中的消息记录表（存储用户、AI、客服的所有对话内容）
 * @TableName chat_message
 */
@TableName(value ="chat_message")
@Data
public class ChatMessage {
    /**
     * 消息主键ID（唯一标识）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属会话ID（关联chat_session表id，标识消息归属的会话）
     */
    private Long sessionId;

    /**
     * 消息发送者类型（0-用户发送，1-AI回复，-1-人工客服回复）
     */
    private Integer messageType;

    /**
     * 消息内容（文本格式，存储用户提问、AI回答或客服回复）
     */
    private String content;

    /**
     * 消息创建时间（发送时间）
     */
    private Date createdAt;
}