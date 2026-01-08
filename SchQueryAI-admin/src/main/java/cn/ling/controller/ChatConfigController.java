package cn.ling.controller;

import cn.ling.Result;
import cn.ling.dto.ChatDefaultConfigDTO;
import cn.ling.service.SysConfigService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 默认对话参数配置（对齐 rin-ai：/chat/config/default）
 */
@Slf4j
@RestController
@RequestMapping("/chat/config")
public class ChatConfigController {

    private static final String KEY_MODEL = "chat.default.model";
    private static final String KEY_KID = "chat.default.kid";
    private static final String KEY_KNAME = "chat.default.kName";
    private static final String KEY_TALK_COUNT = "chat.default.talkCount";
    private static final String KEY_MAX_TOKENS = "chat.default.max_tokens";
    private static final String KEY_SYSTEM_MESSAGE = "chat.default.systemMessage";
    private static final String KEY_TEMPERATURE = "chat.default.temperature";
    private static final String KEY_TOP_P = "chat.default.top_p";
    private static final String KEY_PRESENCE_PENALTY = "chat.default.presence_penalty";
    private static final String KEY_FREQUENCY_PENALTY = "chat.default.frequency_penalty";
    private static final String KEY_REPETITION_PENALTY = "chat.default.repetition_penalty";
    private static final String KEY_MCP_SERVERS = "chat.default.mcpServers";
    private static final String KEY_MCP_MODE = "chat.default.mcpMode";
    private static final String KEY_PROMPT_MERGE_MODE = "chat.default.promptMergeMode";
    private static final String KEY_WEIGHT_USER = "chat.weight.user";
    private static final String KEY_WEIGHT_KNOWLEDGE = "chat.weight.knowledge";
    private static final String KEY_WEIGHT_MCP = "chat.weight.mcp";

    @Resource
    private SysConfigService sysConfigService;

    @GetMapping("/default")
    public Result<ChatDefaultConfigDTO> getDefaultConfig() {
        ChatDefaultConfigDTO dto = new ChatDefaultConfigDTO();
        dto.setModel(sysConfigService.getConfigValue(KEY_MODEL));
        dto.setKid(sysConfigService.getConfigValue(KEY_KID));
        dto.setKName(sysConfigService.getConfigValue(KEY_KNAME));
        dto.setTalkCount(parseInt(sysConfigService.getConfigValue(KEY_TALK_COUNT), null));
        dto.setMax_tokens(parseInt(sysConfigService.getConfigValue(KEY_MAX_TOKENS), null));
        dto.setSystemMessage(sysConfigService.getConfigValue(KEY_SYSTEM_MESSAGE));
        dto.setTemperature(parseDouble(sysConfigService.getConfigValue(KEY_TEMPERATURE), null));
        dto.setTop_p(parseDouble(sysConfigService.getConfigValue(KEY_TOP_P), null));
        dto.setPresence_penalty(parseDouble(sysConfigService.getConfigValue(KEY_PRESENCE_PENALTY), null));
        dto.setFrequency_penalty(parseDouble(sysConfigService.getConfigValue(KEY_FREQUENCY_PENALTY), null));
        dto.setRepetition_penalty(parseDouble(sysConfigService.getConfigValue(KEY_REPETITION_PENALTY), null));
        dto.setMcpServers(sysConfigService.getConfigValue(KEY_MCP_SERVERS));
        dto.setMcpMode(sysConfigService.getConfigValue(KEY_MCP_MODE));
        dto.setPromptMergeMode(normalizePromptMergeMode(sysConfigService.getConfigValue(KEY_PROMPT_MERGE_MODE)));
        dto.setWeightUser(parseDouble(sysConfigService.getConfigValue(KEY_WEIGHT_USER), 0.6));
        dto.setWeightKnowledge(parseDouble(sysConfigService.getConfigValue(KEY_WEIGHT_KNOWLEDGE), 0.25));
        dto.setWeightMcp(parseDouble(sysConfigService.getConfigValue(KEY_WEIGHT_MCP), 0.15));
        return Result.success(dto);
    }

    @PutMapping("/default")
    public Result<String> setDefaultConfig(@RequestBody ChatDefaultConfigDTO dto) {
        if (dto == null) {
            return Result.error(400, "参数不能为空");
        }

        upsert(KEY_MODEL, "默认对话模型", trimToNull(dto.getModel()), "默认对话参数");
        upsert(KEY_KID, "默认知识库ID", trimToNull(dto.getKid()), "默认对话参数");
        upsert(KEY_KNAME, "默认知识库名称", trimToNull(dto.getKName()), "默认对话参数");
        upsert(KEY_TALK_COUNT, "默认上下文轮数", dto.getTalkCount() == null ? null : String.valueOf(dto.getTalkCount()), "默认对话参数");
        upsert(KEY_MAX_TOKENS, "最大输出token", dto.getMax_tokens() == null ? null : String.valueOf(dto.getMax_tokens()), "默认对话参数");
        upsert(KEY_SYSTEM_MESSAGE, "系统提示词", trimToNull(dto.getSystemMessage()), "默认对话参数");
        upsert(KEY_TEMPERATURE, "temperature", dto.getTemperature() == null ? null : String.valueOf(dto.getTemperature()), "默认对话参数");
        upsert(KEY_TOP_P, "top_p", dto.getTop_p() == null ? null : String.valueOf(dto.getTop_p()), "默认对话参数");
        upsert(KEY_PRESENCE_PENALTY, "presence_penalty", dto.getPresence_penalty() == null ? null : String.valueOf(dto.getPresence_penalty()), "默认对话参数");
        upsert(KEY_FREQUENCY_PENALTY, "frequency_penalty", dto.getFrequency_penalty() == null ? null : String.valueOf(dto.getFrequency_penalty()), "默认对话参数");
        upsert(KEY_REPETITION_PENALTY, "repetition_penalty", dto.getRepetition_penalty() == null ? null : String.valueOf(dto.getRepetition_penalty()), "默认对话参数");
        upsert(KEY_MCP_SERVERS, "MCP服务URL列表", trimToNull(dto.getMcpServers()), "MCP配置");
        upsert(KEY_MCP_MODE, "MCP检索策略", normalizeMcpMode(dto.getMcpMode()), "MCP配置");
        if (dto.getPromptMergeMode() != null) {
            upsert(KEY_PROMPT_MERGE_MODE, "提示词融合模式", normalizePromptMergeMode(dto.getPromptMergeMode()), "提示词融合策略");
        }
        if (dto.getWeightUser() != null) {
            upsert(KEY_WEIGHT_USER, "用户提示词权重", String.valueOf(normalizeWeight(dto.getWeightUser(), 0.6)), "提示词融合策略");
        }
        if (dto.getWeightKnowledge() != null) {
            upsert(KEY_WEIGHT_KNOWLEDGE, "知识库权重", String.valueOf(normalizeWeight(dto.getWeightKnowledge(), 0.25)), "提示词融合策略");
        }
        if (dto.getWeightMcp() != null) {
            upsert(KEY_WEIGHT_MCP, "MCP权重", String.valueOf(normalizeWeight(dto.getWeightMcp(), 0.15)), "提示词融合策略");
        }

        return Result.success("保存成功");
    }

    private void upsert(String key, String name, String value, String remark) {
        sysConfigService.upsertConfig(key, value, name, remark);
    }

    private static String trimToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static Integer parseInt(String raw, Integer def) {
        if (!StringUtils.hasText(raw)) {
            return def;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return def;
        }
    }

    private static Double parseDouble(String raw, Double def) {
        if (!StringUtils.hasText(raw)) {
            return def;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (Exception ignored) {
            return def;
        }
    }

    private static String normalizeMcpMode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "fallback";
        }
        String normalized = raw.trim().toLowerCase();
        return switch (normalized) {
            case "off", "false", "0", "disable", "disabled" -> "off";
            case "merge", "both", "all" -> "merge";
            case "fallback", "kb_then_mcp", "knowledge_then_mcp" -> "fallback";
            default -> "fallback";
        };
    }

    private static String normalizePromptMergeMode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "user_first";
        }
        String normalized = raw.trim().toLowerCase();
        return switch (normalized) {
            case "user_first", "user", "userfirst" -> "user_first";
            case "knowledge_first", "knowledge", "knowledgefirst" -> "knowledge_first";
            case "balanced", "balance" -> "balanced";
            case "layered", "layer" -> "layered";
            default -> "user_first";
        };
    }

    private static double normalizeWeight(Double raw, double def) {
        if (raw == null || !Double.isFinite(raw)) {
            return def;
        }
        if (raw < 0) {
            return 0;
        }
        if (raw > 1) {
            return 1;
        }
        return raw;
    }
}
