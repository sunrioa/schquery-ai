package cn.ling.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final int MAX_SUGGESTION_COUNT = 3;
    private static final Pattern SCHOOL_NAME_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9]{2,24}(大学|学院|学校|中学|职校|职业技术学院))");
    private static final Pattern QUESTION_TOPIC_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9]{2,24})(怎么样|如何|好吗|有哪些|是什么|值不值得|值得报吗)");
    private static final Pattern NON_TEXT_PATTERN = Pattern.compile("[\\p{Punct}\\p{IsPunctuation}，。？！、；：“”‘’（）()【】\\s]+");

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
        return getSuggestByIntent(intent, null);
    }

    /**
     * 根据意图获取追问建议（快速路径）
     *
     * @param intent 意图名称（中文）
     * @param sessionId 会话ID（用于提取最近用户问题主题，增强上下文贴合度）
     * @return 追问建议列表
     */
    public SuggestVO getSuggestByIntent(String intent, Long sessionId) {
        if (!isSuggestEnabled()) {
            return new SuggestVO(false, null);
        }

        String normalizedIntent = normalizeIntent(intent);
        List<String> suggestions = buildFastSuggestions(normalizedIntent, sessionId);
        if (suggestions != null && !suggestions.isEmpty()) {
            return new SuggestVO(true, suggestions);
        }

        return new SuggestVO(true, defaultFallbackSuggestions(normalizedIntent));
    }

    /**
     * 基于对话上下文生成追问建议（用于 UNKNOWN 意图）
     *
     * @param sessionId 会话ID
     * @return 追问建议列表
     */
    public SuggestVO generateSuggestByContext(Long sessionId) {
        // 检查是否启用追问建议
        if (!isSuggestEnabled()) {
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
                Integer messageType = parseMessageType(msg.get("message_type"));
                String content = msg.get("content") == null ? "" : String.valueOf(msg.get("content"));
                String role = messageType == 0 ? "用户" : "助手";
                contextBuilder.append(role).append("：").append(content).append("\n\n");
            }

            // 获取追问建议提示词（使用角色配置的系统提示词）
            String systemPrompt = preset.getSystemMessage();
            if (!StringUtils.hasText(systemPrompt)) {
                systemPrompt = "你是一个智能对话助手，基于对话内容预测用户可能关心的问题。";
            }

            String userPrompt = contextBuilder.toString() +
                "请基于以上对话内容，输出3条可直接点击发送的追问问题。" +
                "要求：必须使用用户提问口吻，不能使用“需要我/要不要我/我可以/我来”等助手口吻；" +
                "每条问题独立完整，避免“这个/它/上述”等指代；" +
                "每条不超过22个字；" +
                "只返回JSON：{\"questions\": [\"问题1\", \"问题2\", \"问题3\"]}";

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
            if (questions == null || questions.isEmpty()) {
                questions = buildFastSuggestions("UNKNOWN", sessionId);
            }

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
            if (!StringUtils.hasText(response)) {
                return null;
            }

            String cleaned = stripMarkdownFence(response);

            // 优先按 JSON 解析（兼容 questions / suggestions / followUpQuestions 等字段名）
            List<String> fromJson = extractQuestionsFromJson(cleaned);
            if (fromJson != null && !fromJson.isEmpty()) {
                return fromJson;
            }

            // 备用解析：按行提取并过滤 JSON 结构符号行
            String[] lines = cleaned.split("\n");
            Set<String> dedup = new LinkedHashSet<>();
            for (String line : lines) {
                String normalized = normalizeSuggestionText(line);
                if (normalized != null) {
                    dedup.add(normalized);
                }
                if (dedup.size() >= MAX_SUGGESTION_COUNT) {
                    break;
                }
            }
            return dedup.isEmpty() ? null : new ArrayList<>(dedup);
        } catch (Exception e) {
            log.error("解析追问建议失败: {}", e.getMessage());
            return null;
        }
    }

    private String stripMarkdownFence(String raw) {
        String response = raw == null ? "" : raw.trim();
        if (!response.startsWith("```")) {
            return response;
        }
        int firstBreak = response.indexOf('\n');
        if (firstBreak < 0 || firstBreak >= response.length() - 1) {
            return response;
        }
        String body = response.substring(firstBreak + 1);
        int tail = body.lastIndexOf("```");
        if (tail >= 0) {
            body = body.substring(0, tail);
        }
        return body.trim();
    }

    private List<String> extractQuestionsFromJson(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            JsonElement root = JsonParser.parseString(text);
            if (root == null || root.isJsonNull()) {
                return null;
            }
            if (root.isJsonArray()) {
                return extractFromArray(root.getAsJsonArray());
            }
            if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                List<String> fromObject = extractFromObject(obj);
                if (fromObject != null && !fromObject.isEmpty()) {
                    return fromObject;
                }
                JsonElement data = obj.get("data");
                if (data != null && data.isJsonObject()) {
                    return extractFromObject(data.getAsJsonObject());
                }
            }
        } catch (Exception ignored) {
            // 非 JSON 文本走后续行解析
        }
        return null;
    }

    private List<String> extractFromObject(JsonObject obj) {
        if (obj == null) {
            return null;
        }
        // 常见字段名优先
        String[] keys = new String[]{"questions", "suggestions", "followUpQuestions", "followupQuestions", "follow_up_questions"};
        for (String key : keys) {
            JsonElement element = obj.get(key);
            if (element != null && element.isJsonArray()) {
                List<String> parsed = extractFromArray(element.getAsJsonArray());
                if (parsed != null && !parsed.isEmpty()) {
                    return parsed;
                }
            }
        }
        // 再兜底：寻找包含 question/suggest 的数组字段
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (entry.getValue() == null || !entry.getValue().isJsonArray()) {
                continue;
            }
            String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase();
            if (key.contains("question") || key.contains("suggest")) {
                List<String> parsed = extractFromArray(entry.getValue().getAsJsonArray());
                if (parsed != null && !parsed.isEmpty()) {
                    return parsed;
                }
            }
        }
        return null;
    }

    private List<String> extractFromArray(JsonArray array) {
        if (array == null || array.isEmpty()) {
            return null;
        }
        Set<String> dedup = new LinkedHashSet<>();
        for (JsonElement element : array) {
            if (element == null || element.isJsonNull()) {
                continue;
            }
            String text = null;
            if (element.isJsonPrimitive()) {
                text = element.getAsString();
            } else if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                JsonElement q = obj.get("question");
                if (q != null && q.isJsonPrimitive()) {
                    text = q.getAsString();
                }
            }
            String normalized = normalizeSuggestionText(text);
            if (normalized != null) {
                dedup.add(normalized);
            }
            if (dedup.size() >= MAX_SUGGESTION_COUNT) {
                break;
            }
        }
        return dedup.isEmpty() ? null : new ArrayList<>(dedup);
    }

    private String normalizeSuggestionText(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String line = text.trim();
        line = line.replaceFirst("^[0-9]+[.、)]\\s*", "");
        line = line.replaceFirst("^[-*•]\\s*", "");
        line = line.replaceAll("^[\"'`]+|[\"'`,]+$", "").trim();
        if (!StringUtils.hasText(line)) {
            return null;
        }
        // 过滤 JSON 结构字符和字段名行，避免渲染成 {、]、\"followUpQuestions\":[
        if ("{".equals(line) || "}".equals(line) || "[".equals(line) || "]".equals(line) || ",".equals(line)) {
            return null;
        }
        if (line.matches("^\"?[a-zA-Z_]+\"?\\s*:\\s*\\[?$")) {
            return null;
        }
        if (isAssistantPerspective(line)) {
            return null;
        }
        if (line.length() < 4 || line.length() > 80) {
            return null;
        }
        line = line.replaceAll("[。!！]+$", "？");
        if (!line.endsWith("？") && !line.endsWith("?")) {
            line = line + "？";
        }
        return line;
    }

    private boolean isAssistantPerspective(String line) {
        if (!StringUtils.hasText(line)) {
            return false;
        }
        String text = line.trim();
        return text.startsWith("需要我")
                || text.startsWith("要不要我")
                || text.startsWith("是否需要我")
                || text.startsWith("我可以")
                || text.startsWith("我来")
                || text.startsWith("欢迎告诉我")
                || text.contains("告诉我你的")
                || text.contains("告诉我你更");
    }

    private boolean isSuggestEnabled() {
        return "true".equalsIgnoreCase(sysConfigService.getConfigValue(KEY_SUGGEST_USE));
    }

    private String normalizeIntent(String intent) {
        if (!StringUtils.hasText(intent)) {
            return "UNKNOWN";
        }
        String normalized = intent.trim();
        switch (normalized.toUpperCase()) {
            case "Q":
                return "专业信息";
            case "R":
                return "招生计划";
            case "S":
                return "历年分数线";
            case "T":
                return "招生政策";
            case "U":
                return "报考指南";
            case "V":
                return "校园信息";
            case "W":
                return "UNKNOWN";
            default:
                break;
        }
        if ("未知".equalsIgnoreCase(normalized) || "other".equalsIgnoreCase(normalized) || "others".equalsIgnoreCase(normalized)) {
            return "UNKNOWN";
        }
        return normalized;
    }

    private List<String> buildFastSuggestions(String intent, Long sessionId) {
        ConversationContext context = buildConversationContext(sessionId);

        List<String> pool = buildTopicAwareSuggestions(intent, context.topic());
        if (pool == null || pool.isEmpty()) {
            pool = PRESET_SUGGESTIONS.get(intent);
        }

        List<String> normalizedPool = normalizePool(pool);
        List<String> filteredPool = filterRecentlyAsked(normalizedPool, context.recentUserQuestions());
        List<String> result = pickByRotation(filteredPool, context.rotationSeed(), MAX_SUGGESTION_COUNT);

        if (result.size() < MAX_SUGGESTION_COUNT) {
            List<String> fallbackPool = normalizePool(defaultFallbackSuggestions(intent));
            List<String> fallbackFiltered = filterRecentlyAsked(fallbackPool, context.recentUserQuestions());
            appendByRotation(result, fallbackFiltered, context.rotationSeed() + 7, MAX_SUGGESTION_COUNT);
        }

        if (result.size() < MAX_SUGGESTION_COUNT && !normalizedPool.isEmpty()) {
            appendByRotation(result, normalizedPool, context.rotationSeed() + 13, MAX_SUGGESTION_COUNT);
        }

        return result.isEmpty() ? null : result;
    }

    private List<String> buildTopicAwareSuggestions(String intent, String topic) {
        if (!StringUtils.hasText(topic)) {
            return null;
        }
        switch (intent) {
            case "专业信息":
                return List.of(
                        topic + "有哪些优势专业值得重点了解",
                        topic + "最强学科和特色方向分别是什么",
                        "报考" + topic + "时专业该怎么优先排序",
                        topic + "的王牌专业就业方向主要有哪些",
                        topic + "不同专业的培养方案差异大吗",
                        "在" + topic + "里哪些专业更适合继续深造"
                );
            case "招生计划":
                return List.of(
                        topic + "今年各省招生计划大概是多少",
                        topic + "今年是否有新增或缩招专业",
                        topic + "文理科（或首选科目）计划有差异吗",
                        topic + "专项计划在各省的名额如何分配",
                        "报考" + topic + "时哪些专业竞争最激烈"
                );
            case "历年分数线":
                return List.of(
                        topic + "近三年的录取分数和位次是多少",
                        "按我的分数报" + topic + "属于冲稳保哪一档",
                        topic + "热门专业和普通专业分差有多大",
                        topic + "在不同省份分数线差异大吗",
                        topic + "近三年最低录取位次变化趋势如何"
                );
            case "招生政策":
                return List.of(
                        "报考" + topic + "有哪些硬性条件需要满足",
                        topic + "的录取规则和投档方式是什么",
                        topic + "是否有专项计划或加分政策",
                        topic + "转专业政策和限制条件是什么",
                        "报考" + topic + "需要重点关注哪些时间节点"
                );
            case "报考指南":
                return List.of(
                        "报考" + topic + "时志愿顺序怎么填更稳妥",
                        "以我的分数报" + topic + "推荐哪些专业组合",
                        "报" + topic + "前需要重点核对哪些材料",
                        "报" + topic + "时冲稳保比例怎么分配更合理",
                        topic + "适合什么分数段的考生优先选择"
                );
            case "校园信息":
                return List.of(
                        topic + "的住宿和生活条件整体怎么样",
                        topic + "有哪些奖助学金和资助渠道",
                        topic + "校园学习资源和实训机会多吗",
                        topic + "本科生科研和竞赛支持力度如何",
                        topic + "食堂、图书馆和运动设施使用体验如何"
                );
            case "UNKNOWN":
                return List.of(
                        topic + "近三年的录取分数和位次是多少",
                        topic + "有哪些专业值得优先关注",
                        "报考" + topic + "需要重点看哪些政策",
                        topic + "的优势学科和就业方向有哪些",
                        "报考" + topic + "时志愿该如何搭配更稳妥"
                );
            default:
                return null;
        }
    }

    private ConversationContext buildConversationContext(Long sessionId) {
        if (sessionId == null) {
            return ConversationContext.empty();
        }
        try {
            List<Map<String, Object>> messageList = commonMapper.getChatMessages(sessionId, 20);
            if (messageList == null || messageList.isEmpty()) {
                return ConversationContext.empty();
            }
            String topic = null;
            String latestUserQuestion = null;
            int userTurnCount = 0;
            List<String> recentUserQuestions = new ArrayList<>();

            for (Map<String, Object> msg : messageList) {
                Integer messageType = parseMessageType(msg.get("message_type"));
                if (messageType == null || messageType != 0) {
                    continue;
                }
                userTurnCount += 1;
                String content = msg.get("content") == null ? null : String.valueOf(msg.get("content"));
                if (!StringUtils.hasText(content)) {
                    continue;
                }
                String trimmed = content.trim();
                if (!StringUtils.hasText(latestUserQuestion)) {
                    latestUserQuestion = trimmed;
                }
                if (recentUserQuestions.size() < 8) {
                    recentUserQuestions.add(trimmed);
                }
                if (!StringUtils.hasText(topic)) {
                    String extracted = extractTopicFromText(trimmed);
                    if (StringUtils.hasText(extracted)) {
                        topic = extracted;
                    }
                }
            }
            return new ConversationContext(topic, recentUserQuestions, userTurnCount, latestUserQuestion);
        } catch (Exception e) {
            log.debug("构建追问上下文失败, sessionId={}, msg={}", sessionId, e.getMessage());
            return ConversationContext.empty();
        }
    }

    private List<String> normalizePool(List<String> pool) {
        if (pool == null || pool.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> dedup = new LinkedHashSet<>();
        for (String item : pool) {
            String normalized = normalizeSuggestionText(item);
            if (normalized != null) {
                dedup.add(normalized);
            }
        }
        return dedup.isEmpty() ? Collections.emptyList() : new ArrayList<>(dedup);
    }

    private List<String> filterRecentlyAsked(List<String> suggestions, List<String> recentUserQuestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return Collections.emptyList();
        }
        if (recentUserQuestions == null || recentUserQuestions.isEmpty()) {
            return new ArrayList<>(suggestions);
        }
        List<String> filtered = new ArrayList<>();
        for (String suggestion : suggestions) {
            if (!isAskedRecently(suggestion, recentUserQuestions)) {
                filtered.add(suggestion);
            }
        }
        return filtered;
    }

    private boolean isAskedRecently(String suggestion, List<String> recentUserQuestions) {
        String normalizedSuggestion = canonicalizeText(suggestion);
        if (!StringUtils.hasText(normalizedSuggestion)) {
            return false;
        }
        for (String question : recentUserQuestions) {
            String normalizedQuestion = canonicalizeText(question);
            if (!StringUtils.hasText(normalizedQuestion)) {
                continue;
            }
            if (normalizedQuestion.equals(normalizedSuggestion)
                    || normalizedQuestion.contains(normalizedSuggestion)
                    || normalizedSuggestion.contains(normalizedQuestion)) {
                return true;
            }
        }
        return false;
    }

    private List<String> pickByRotation(List<String> source, int seed, int limit) {
        List<String> result = new ArrayList<>();
        appendByRotation(result, source, seed, limit);
        return result;
    }

    private void appendByRotation(List<String> target, List<String> source, int seed, int limit) {
        if (target == null || source == null || source.isEmpty() || target.size() >= limit) {
            return;
        }
        int start = Math.floorMod(seed, source.size());
        for (int i = 0; i < source.size() && target.size() < limit; i++) {
            String candidate = source.get((start + i) % source.size());
            if (!target.contains(candidate)) {
                target.add(candidate);
            }
        }
    }

    private String canonicalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return NON_TEXT_PATTERN.matcher(text).replaceAll("").toLowerCase();
    }

    private Integer parseMessageType(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private String extractTopicFromText(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        Matcher schoolMatcher = SCHOOL_NAME_PATTERN.matcher(normalized);
        if (schoolMatcher.find()) {
            return schoolMatcher.group(1);
        }
        Matcher topicMatcher = QUESTION_TOPIC_PATTERN.matcher(normalized);
        if (topicMatcher.find()) {
            return topicMatcher.group(1);
        }
        if (normalized.length() <= 16 && normalized.matches("^[\\u4e00-\\u9fa5A-Za-z0-9·•\\-]+$")) {
            return normalized;
        }
        return null;
    }

    private List<String> defaultFallbackSuggestions(String intent) {
        if ("UNKNOWN".equalsIgnoreCase(intent)) {
            return List.of(
                    "我更应该先关注这所学校的哪些核心信息",
                    "这所学校近三年的录取分数和位次是多少",
                    "报考这所学校时有哪些关键政策要确认",
                    "这所学校哪些专业更适合我的分数段",
                    "这所学校和同层次院校相比优势在哪里",
                    "报考这所学校时志愿顺序怎么安排更稳妥"
            );
        }
        return List.of(
                "这个问题按年份对比后会有什么变化",
                "如果结合我的分数报考，建议应该怎么调整",
                "和同层次院校相比，这个结论有什么差异",
                "这个问题对应的关键政策依据是什么",
                "这个结论在不同省份会有明显差异吗",
                "下一步我应该优先补充哪些信息再判断"
        );
    }

    private static final class ConversationContext {
        private final String topic;
        private final List<String> recentUserQuestions;
        private final int userTurnCount;
        private final String latestUserQuestion;

        private ConversationContext(String topic, List<String> recentUserQuestions, int userTurnCount, String latestUserQuestion) {
            this.topic = topic;
            this.recentUserQuestions = recentUserQuestions == null ? Collections.emptyList() : recentUserQuestions;
            this.userTurnCount = userTurnCount;
            this.latestUserQuestion = latestUserQuestion;
        }

        private static ConversationContext empty() {
            return new ConversationContext(null, Collections.emptyList(), 0, null);
        }

        private String topic() {
            return topic;
        }

        private List<String> recentUserQuestions() {
            return recentUserQuestions;
        }

        private int rotationSeed() {
            String normalizedLatest = latestUserQuestion == null ? "" : NON_TEXT_PATTERN.matcher(latestUserQuestion).replaceAll("");
            int latestHash = normalizedLatest.isEmpty() ? 0 : normalizedLatest.hashCode();
            return userTurnCount * 31 + latestHash;
        }
    }
}
