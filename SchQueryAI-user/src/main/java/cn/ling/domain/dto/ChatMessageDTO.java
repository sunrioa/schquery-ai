package cn.ling.domain.dto;

import lombok.Data;


@Data
public class ChatMessageDTO {
    /**
     * 消息主键ID（唯一标识）
     */
    private Long id;

    /**
     * 所属会话ID（关联chat_session表id，标识消息归属的会话）
     */
    private Long sessionId;


    /**
     * 消息内容（文本格式，存储用户提问、AI回答或客服回复）
     */
    private String content;

}
