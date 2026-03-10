package cn.ling.controller;

import cn.ling.Result;
import cn.ling.dto.ChatDefaultConfigDTO;
import cn.ling.service.SysConfigService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

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
    private static final String KEY_WELCOME_MESSAGE = "chat.default.message";

    // 追问建议配置
    private static final String KEY_SUGGEST_USE = "suggest.use";
    private static final String KEY_SUGGEST_CHAT_TURN = "suggest.chatTurn";
    private static final String KEY_SUGGEST_ROLE = "suggest.role";

    // 意图识别增强配置
    private static final String KEY_INTENT_PROMPT_ENABLED = "intent.prompt.enabled";
    private static final String KEY_INTENT_PROMPT_MAP = "intent.prompt.map";

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

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
        dto.setWelcomeMessage(sysConfigService.getConfigValue(KEY_WELCOME_MESSAGE));

        // 追问建议配置
        ChatDefaultConfigDTO.SuggestConfig suggestConfig = new ChatDefaultConfigDTO.SuggestConfig();
        suggestConfig.setEnabled(sysConfigService.getConfigValue(KEY_SUGGEST_USE));
        suggestConfig.setChatTurn(sysConfigService.getConfigValue(KEY_SUGGEST_CHAT_TURN));
        suggestConfig.setRoleId(sysConfigService.getConfigValue(KEY_SUGGEST_ROLE));
        dto.setSuggestConfig(suggestConfig);

        // 意图识别增强配置
        ChatDefaultConfigDTO.IntentConfig intentConfig = new ChatDefaultConfigDTO.IntentConfig();
        intentConfig.setEnabled(sysConfigService.getConfigValue(KEY_INTENT_PROMPT_ENABLED));
        intentConfig.setPromptMap(parsePromptMap(sysConfigService.getConfigValue(KEY_INTENT_PROMPT_MAP)));
        dto.setIntentConfig(intentConfig);

        return Result.success(dto);
    }

    @PutMapping("/default")
    public Result<String> setDefaultConfig(@RequestBody ChatDefaultConfigDTO dto) {
        if (dto == null) {
            return Result.error(400, "参数不能为空");
        }

        // 仅更新前端本次传入的字段，避免局部保存时覆盖其他配置
        if (dto.getModel() != null) {
            upsert(KEY_MODEL, "默认对话模型", trimToNull(dto.getModel()), "默认对话参数");
        }
        if (dto.getKid() != null) {
            upsert(KEY_KID, "默认知识库ID", trimToNull(dto.getKid()), "默认对话参数");
        }
        if (dto.getKName() != null) {
            upsert(KEY_KNAME, "默认知识库名称", trimToNull(dto.getKName()), "默认对话参数");
        }
        if (dto.getTalkCount() != null) {
            upsert(KEY_TALK_COUNT, "默认上下文轮数", String.valueOf(dto.getTalkCount()), "默认对话参数");
        }
        if (dto.getMax_tokens() != null) {
            upsert(KEY_MAX_TOKENS, "最大输出token", String.valueOf(dto.getMax_tokens()), "默认对话参数");
        }
        if (dto.getSystemMessage() != null) {
            upsert(KEY_SYSTEM_MESSAGE, "系统提示词", trimToNull(dto.getSystemMessage()), "默认对话参数");
        }
        if (dto.getTemperature() != null) {
            upsert(KEY_TEMPERATURE, "temperature", String.valueOf(dto.getTemperature()), "默认对话参数");
        }
        if (dto.getTop_p() != null) {
            upsert(KEY_TOP_P, "top_p", String.valueOf(dto.getTop_p()), "默认对话参数");
        }
        if (dto.getPresence_penalty() != null) {
            upsert(KEY_PRESENCE_PENALTY, "presence_penalty", String.valueOf(dto.getPresence_penalty()), "默认对话参数");
        }
        if (dto.getFrequency_penalty() != null) {
            upsert(KEY_FREQUENCY_PENALTY, "frequency_penalty", String.valueOf(dto.getFrequency_penalty()), "默认对话参数");
        }
        if (dto.getRepetition_penalty() != null) {
            upsert(KEY_REPETITION_PENALTY, "repetition_penalty", String.valueOf(dto.getRepetition_penalty()), "默认对话参数");
        }
        if (dto.getMcpServers() != null) {
            upsert(KEY_MCP_SERVERS, "MCP服务URL列表", trimToNull(dto.getMcpServers()), "MCP配置");
        }
        if (dto.getMcpMode() != null) {
            upsert(KEY_MCP_MODE, "MCP检索策略", normalizeMcpMode(dto.getMcpMode()), "MCP配置");
        }
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
        if (dto.getWelcomeMessage() != null) {
            upsert(KEY_WELCOME_MESSAGE, "欢迎消息", trimToNull(dto.getWelcomeMessage()), "对话管理");
        }

        // 保存追问建议配置
        if (dto.getSuggestConfig() != null) {
            ChatDefaultConfigDTO.SuggestConfig suggestConfig = dto.getSuggestConfig();
            upsert(KEY_SUGGEST_USE, "追问建议启用", suggestConfig.getEnabled(), "追问建议");
            upsert(KEY_SUGGEST_CHAT_TURN, "追问建议对话轮次", suggestConfig.getChatTurn(), "追问建议");
            upsert(KEY_SUGGEST_ROLE, "追问建议角色", trimToNull(suggestConfig.getRoleId()), "追问建议");
        }

        // 保存意图识别增强配置
        if (dto.getIntentConfig() != null) {
            ChatDefaultConfigDTO.IntentConfig intentConfig = dto.getIntentConfig();
            upsert(KEY_INTENT_PROMPT_ENABLED, "意图增强提示词启用", intentConfig.getEnabled(), "意图识别增强");
            upsert(KEY_INTENT_PROMPT_MAP, "意图增强提示词映射", toPromptMapJson(intentConfig.getPromptMap()), "意图识别增强");
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

    private static Map<String, String> parsePromptMap(String raw) {
        Map<String, String> defaults = defaultIntentPromptMap();
        if (!StringUtils.hasText(raw)) {
            return defaults;
        }
        try {
            Map<String, String> parsed = GSON.fromJson(raw, MAP_TYPE);
            if (parsed == null || parsed.isEmpty()) {
                return defaults;
            }
            Map<String, String> normalized = normalizePromptMap(parsed);
            defaults.putAll(normalized);
            return defaults;
        } catch (Exception ignored) {
            return defaults;
        }
    }

    private static String toPromptMapJson(Map<String, String> promptMap) {
        return GSON.toJson(normalizePromptMap(promptMap));
    }

    private static Map<String, String> normalizePromptMap(Map<String, String> raw) {
        Map<String, String> normalized = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return normalized;
        }
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            String key = trimToNull(entry.getKey());
            String value = trimToNull(entry.getValue());
            if (key != null && value != null) {
                normalized.put(key, value);
            }
        }
        return normalized;
    }

    private static Map<String, String> defaultIntentPromptMap() {
        Map<String, String> defaults = new HashMap<>();
        defaults.put("专业信息", "回答应优先给出专业特点、核心课程、培养方向和就业去向，并适当做专业对比。");
        defaults.put("招生计划", "回答应聚焦招生人数、省份名额、专业变化，优先给结构化列表并标注年份。");
        defaults.put("历年分数线", "回答应优先给近3年分数线、位次变化及报考建议，避免只给单一年份。");
        defaults.put("招生政策", "回答应明确政策条款、适用对象、时间节点，涉及不确定内容时提醒以官方公告为准。");
        defaults.put("报考指南", "回答应按步骤给出志愿填报建议，并提供不同分数段/风险偏好的策略。");
        defaults.put("校园信息", "回答应覆盖学习生活、住宿餐饮、奖助学金与校园资源，突出实用信息。");
        defaults.put("UNKNOWN", "若意图不明确，先做问题澄清，再给出可选追问方向。");
        return defaults;
    }
}
