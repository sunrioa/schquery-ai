package cn.ling.dto;

import lombok.Data;

/**
 * 默认对话参数（管理端配置 / 用户端读取）
 * 字段命名对齐 rin-ai 的 ChatDefaultConfigVo，便于后续迁移与前端复用。
 */
@Data
public class ChatDefaultConfigDTO {

    /**
     * 默认对话模型名称（chat_model.model_name）
     */
    private String model;

    /**
     * 默认知识库ID（knowledge_info.id）
     */
    private String kid;

    /**
     * 默认知识库名称（冗余展示）
     */
    private String kName;

    /**
     * 默认上下文轮数
     */
    private Integer talkCount;

    /**
     * 最大输出 token
     */
    private Integer max_tokens;

    /**
     * 系统提示词
     */
    private String systemMessage;

    private Double temperature;

    private Double top_p;

    private Double presence_penalty;

    private Double frequency_penalty;

    private Double repetition_penalty;

    /**
     * MCP服务URL配置（多个用逗号分隔）
     */
    private String mcpServers;

    /**
     * MCP 检索策略：off / fallback / merge
     */
    private String mcpMode;
}

