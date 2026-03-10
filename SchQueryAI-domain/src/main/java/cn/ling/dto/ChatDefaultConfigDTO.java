package cn.ling.dto;

import lombok.Data;

import java.util.Map;

/**
 * 默认对话参数（管理端配置 / 用户端读取）
 * 字段命名对齐 rin-ai 的 ChatDefaultConfigVo，便于后续迁移与前端复用。
 */
@Data
public class ChatDefaultConfigDTO {

    /**
     * 追问建议配置
     */
    private SuggestConfig suggestConfig;

    /**
     * 意图识别增强配置
     */
    private IntentConfig intentConfig;

    /**
     * 追问建议配置
     */
    @Data
    public static class SuggestConfig {
        /**
         * 是否启用
         */
        private String enabled;

        /**
         * 上下文对话轮次（1-3轮）
         */
        private String chatTurn;

        /**
         * 使用角色ID
         */
        private String roleId;
    }

    /**
     * 意图识别增强配置
     */
    @Data
    public static class IntentConfig {
        /**
         * 是否启用意图提示词增强
         */
        private String enabled;

        /**
         * 不同意图对应的提示词模板
         * key=意图名称（如：专业信息）
         * value=该意图下附加到系统提示词的规则
         */
        private Map<String, String> promptMap;
    }

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

    /**
     * 提示词融合模式：user_first / knowledge_first / balanced / layered
     */
    private String promptMergeMode;

    /**
     * 平衡模式下：用户提示词权重（0.0-1.0）
     */
    private Double weightUser;

    /**
     * 平衡模式下：知识库内容权重（0.0-1.0）
     */
    private Double weightKnowledge;

    /**
     * 平衡模式下：MCP内容权重（0.0-1.0）
     */
    private Double weightMcp;

    /**
     * 新会话欢迎消息
     */
    private String welcomeMessage;
}
