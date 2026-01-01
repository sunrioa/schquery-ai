package cn.ling.domain.dto;

import lombok.Data;

@Data
public class ChatSessionDTO {
    /**
     * 会话主键ID（唯一标识）
     */
    private Long id;

    /**
     * 会话名称（用户可自定义，默认"新会话"）
     */
    private String sessionName;

    /**
     * 会话状态（1-活跃，0-已结束） 可作为标记是否已经删除session
     */
    private Integer status;

    /**
     * 对话预设ID（chat_preset.id）
     */
    private Long presetId;

}
