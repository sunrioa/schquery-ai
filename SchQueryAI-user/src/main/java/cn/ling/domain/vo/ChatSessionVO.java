package cn.ling.domain.vo;

import lombok.Data;

import java.util.Date;

@Data
public class ChatSessionVO {

    /**
     * 会话主键ID（唯一标识）
     */
    private Long id;

    /**
     * 会话名称（用户可自定义，默认"新会话"）
     */
    private String sessionName;

    /**
     * 对话预设ID（chat_preset.id）
     */
    private Long presetId;

    /**
     * 最后一条消息的发送时间（用于会话排序）
     */
    private Date lastMessageAt;

}
