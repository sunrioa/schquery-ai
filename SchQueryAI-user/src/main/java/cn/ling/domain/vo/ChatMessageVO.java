package cn.ling.domain.vo;

import lombok.Data;

import java.util.Date;

@Data
public class ChatMessageVO {
    /**
     * 消息主键ID（唯一标识）
     */
    private Long id;

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
