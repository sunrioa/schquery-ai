package cn.ling.service;

import cn.ling.domain.vo.SuggestVO;
import cn.ling.mapper.CommonMapper;
import cn.ling.service.chat.DynamicChatClientService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 追问建议服务
 * 基于用户意图识别结果返回预设追问问题
 */
@Slf4j
@Service
public class SuggestService {

    private static final String KEY_SUGGEST_USE = "suggest.use";
    private static final String KEY_SUGGEST_CHAT_TURN = "suggest.chatTurn";
    private static final String KEY_SUGGEST_ROLE = "suggest.role";

    @Resource
    private SysConfigService sysConfigService;

    @Resource
    private DynamicChatClientService dynamicChatClientService;

    @Resource
    private ChatPresetService chatPresetService;

    @Resource
    private CommonMapper commonMapper;

    /**
     * 预设追问问题映射表
     */
    private static final Map<String, List<String>> PRESET_SUGGESTIONS = new HashMap<>();
    static {
        PRESET_SUGGESTIONS.put("专业信息", List.of(
            "学校有哪些热门专业？",
            "各专业就业前景怎么样？",
            "不同专业的学习方向有什么区别？"
        ));
        PRESET_SUGGESTIONS.put("招生计划", List.of(
            "今年学校总共招多少人？",
            "各省招生名额是怎么分配的？",
            "今年有新增或调整的专业吗？"
        ));
        PRESET_SUGGESTIONS.put("历年分数线", List.of(
            "学校近三年录取分数线是多少？",
            "多少分报考该校比较稳妥？",
            "各专业分数线差距大吗？"
        ));
        PRESET_SUGGESTIONS.put("招生政策", List.of(
            "学校录取规则是什么？",
            "报考有哪些基本要求？",
            "有没有加分或照顾政策？"
        ));
        PRESET_SUGGESTIONS.put("报考指南", List.of(
            "怎么填报该校更稳妥？",
            "志愿填报要注意什么？",
            "如何根据分数选专业？"
        ));
        PRESET_SUGGESTIONS.put("校园信息", List.of(
            "学校住宿条件怎么样？",
            "有哪些奖助学金？",
            "校园实训和生活设施如何？"
        ));
    }

    /**
     * 根据意图获取追问建议
     *
     * @param intent 意图名称（中文）
     * @return 追问建议列表
     */
    public SuggestVO getSuggestByIntent(String intent) {
        // 检查是否启用追问建议
        String enabled = sysConfigService.getConfigValue(KEY_SUGGEST_USE);
        if (!"true".equals(enabled)) {
            return new SuggestVO(false, null);
        }

        // 如果有预设追问，直接返回
        if (PRESET_SUGGESTIONS.containsKey(intent)) {
            return new SuggestVO(true, PRESET_SUGGESTIONS.get(intent));
        }

        // UNKNOWN 意图，返回空（需要调用模型生成的情况）
        return new SuggestVO(true, null);
    }

    /**
     * 基于对话上下文生成追问建议（用于 UNKNOWN 意图）
     *
     * @param sessionId 会话ID
     * @return 追问建议列表
     */
    public SuggestVO generateSuggestByContext(Long sessionId) {
        // 检查是否启用追问建议
        String enabled = sysConfigService.getConfigValue(KEY_SUGGEST_USE);
        if (!"true".equals(enabled)) {
            return new SuggestVO(false, null);
        }

        try {
            // 获取配置的角色ID
            String roleId = sysConfigService.getConfigValue(KEY_SUGGEST_ROLE);
            if (roleId == null || roleId.isEmpty()) {
                return new SuggestVO(true, null);
            }

            // 获取对话轮次
            String chatTurnStr = sysConfigService.getConfigValue(KEY_SUGGEST_CHAT_TURN);
            int chatTurn = chatTurnStr != null ? Integer.parseInt(chatTurnStr) : 2;

            // 获取预设配置
            cn.ling.domain.pojo.ChatPreset preset = chatPresetService.getById(Long.parseLong(roleId));
            if (preset == null || preset.getStatus() != 1) {
                return new SuggestVO(true, null);
            }

            // 从数据库获取历史消息
            List<Map<String, Object>> messageList = commonMapper.getChatMessages(sessionId, chatTurn * 2);

            if (messageList == null || messageList.isEmpty()) {
                return new SuggestVO(true, null);
            }

            // 构建上下文（消息已是按时间倒序，需要反转）
            StringBuilder contextBuilder = new StringBuilder();
            contextBuilder.append("以下是对话历史（最近的").append(chatTurn).append("轮对话）：\n\n");

            // 反向遍历（从旧到新）
            for (int i = messageList.size() - 1; i >= 0; i--) {
                Map<String, Object> msg = messageList.get(i);
                Integer messageType = (Integer) msg.get("message_type");
                String content = (String) msg.get("content");
                String role = messageType == 0 ? "用户" : "助手";
                contextBuilder.append(role).append("：").append(content).append("\n\n");
            }

            // 获取追问建议提示词（使用角色配置的系统提示词）
            String systemPrompt = preset.getSystemMessage();
            if (!StringUtils.hasText(systemPrompt)) {
                systemPrompt = "你是一个智能对话助手，基于对话内容预测用户可能关心的问题。";
            }

            String userPrompt = contextBuilder.toString() +
                "请基于以上对话内容，预测用户可能关心的3个问题。" +
                "要求：每个问题不超过20字，简洁明了。" +
                "直接返回JSON格式：{\"questions\": [\"问题1\", \"问题2\", \"问题3\"]}";

            // 构建完整的 OpenAiChatOptions
            OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                    .model(preset.getModel());

            // 设置可选参数
            if (preset.getMaxTokens() != null && preset.getMaxTokens() > 0) {
                optionsBuilder.maxTokens(preset.getMaxTokens());
            }
            if (preset.getTemperature() != null) {
                optionsBuilder.temperature(preset.getTemperature());
            }
            if (preset.getTopP() != null) {
                optionsBuilder.topP(preset.getTopP());
            }
            if (preset.getPresencePenalty() != null) {
                optionsBuilder.presencePenalty(preset.getPresencePenalty());
            }
            if (preset.getFrequencyPenalty() != null) {
                optionsBuilder.frequencyPenalty(preset.getFrequencyPenalty());
            }

            OpenAiChatOptions openAiChatOptions = optionsBuilder.build();

            // 使用 DynamicChatClientService 获取 ChatClient
            ChatClient client = dynamicChatClientService.resolveClient(preset.getModel());

            // 使用角色的系统提示词
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt)
            ), openAiChatOptions);

            String response = client.prompt(prompt).call().content();

            // 解析响应
            List<String> questions = parseQuestionsFromResponse(response);

            return new SuggestVO(true, questions);

        } catch (Exception e) {
            log.error("生成追问建议失败: {}", e.getMessage(), e);
            return new SuggestVO(true, null);
        }
    }

    /**
     * 从AI响应中解析问题列表
     */
    private List<String> parseQuestionsFromResponse(String response) {
        try {
            // 尝试解析 JSON
            response = response.trim();
            if (response.contains("```json")) {
                response = response.substring(response.indexOf("```json") + 7);
                response = response.substring(0, response.lastIndexOf("```"));
            } else if (response.contains("```")) {
                response = response.substring(response.indexOf("```") + 3);
                response = response.substring(0, response.lastIndexOf("```"));
            }
            response = response.trim();

            // 简单的 JSON 解析
            if (response.contains("\"questions\"")) {
                int start = response.indexOf("[");
                int end = response.lastIndexOf("]");
                if (start > 0 && end > start) {
                    String arrayStr = response.substring(start, end + 1);
                    // 简单解析
                    arrayStr = arrayStr.replace("\"", "").replace("[", "").replace("]", "").replace(",", "\n");
                    String[] parts = arrayStr.split("\n");
                    List<String> result = new ArrayList<>();
                    for (String part : parts) {
                        part = part.trim();
                        if (!part.isEmpty()) {
                            result.add(part);
                        }
                        if (result.size() >= 3) break;
                    }
                    if (!result.isEmpty()) {
                        return result;
                    }
                }
            }

            // 备用解析：按行提取
            String[] lines = response.split("\n");
            List<String> result = new ArrayList<>();
            for (String line : lines) {
                line = line.trim();
                // 移除可能的序号前缀
                line = line.replaceFirst("^[0-9]+[.、)]\\s*", "");
                line = line.replaceFirst("^[-*•]\\s*", "");
                if (!line.isEmpty() && line.length() < 50) {
                    result.add(line);
                }
                if (result.size() >= 3) break;
            }

            return result.isEmpty() ? null : result;
        } catch (Exception e) {
            log.error("解析追问建议失败: {}", e.getMessage());
            return null;
        }
    }
}
