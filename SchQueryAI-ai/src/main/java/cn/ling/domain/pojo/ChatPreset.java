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
 * 对话预设（角色/参数预设）：chat_preset
 */
@TableName(value = "chat_preset")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatPreset {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String presetName;

    /**
     * 模型名称（chat_model.model_name）
     */
    private String model;

    /**
     * 上下文轮数（预留）
     */
    private Integer talkCount;

    /**
     * 最大输出 token
     */
    private Integer maxTokens;

    /**
     * 系统提示词（角色设定）
     */
    private String systemMessage;

    private Double temperature;

    private Double topP;

    private Double presencePenalty;

    private Double frequencyPenalty;

    /**
     * repetition_penalty（预留）
     */
    private Double repetitionPenalty;

    /**
     * 绑定知识库ID（knowledge_info.id）
     */
    private String kid;

    /**
     * MCP运行模式: off/fallback/merge
     */
    private String mcpMode;

    /**
     * MCP服务器地址（逗号分隔）
     */
    private String mcpServers;

    private String remark;

    /**
     * 状态：1-启用 0-禁用
     */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

