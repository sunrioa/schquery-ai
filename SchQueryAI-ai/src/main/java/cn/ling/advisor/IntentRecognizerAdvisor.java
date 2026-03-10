package cn.ling.advisor;

import cn.ling.context.ChatContext;
import cn.ling.rpc.IntentRecognizerRpc;
import cn.ling.service.SysConfigService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 意图识别顾问
 * Spring AI 顾问模式实现，用于在AI对话过程中进行用户意图识别
 * 通过远程调用意图识别服务，将用户输入分类为不同的业务意图类别
 * 识别结果会存储在请求上下文中，供后续处理流程使用
 */
@Slf4j
@Component
public class IntentRecognizerAdvisor implements BaseAdvisor {

    private static final String KEY_INTENT_PROMPT_ENABLED = "intent.prompt.enabled";
    private static final String KEY_INTENT_PROMPT_MAP = "intent.prompt.map";
    private static final String UNKNOWN_INTENT = "UNKNOWN";
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    /**
     * 意图识别远程调用客户端
     * 用于调用外部意图识别服务API
     */
    @Resource
    private IntentRecognizerRpc intentRecognizerRpc;

    @Resource
    private SysConfigService sysConfigService;

    /**
     * 业务意图映射表
     * 定义系统支持的所有意图类别及其对应的中文描述
     * 键：意图代码（单字符标识）
     * 值：意图中文名称
     */
    private static final Map<String, String> BUSINESS_INTENT_MAP = new HashMap<>();
    static {
        BUSINESS_INTENT_MAP.put("Q", "专业信息");
        BUSINESS_INTENT_MAP.put("R", "招生计划");
        BUSINESS_INTENT_MAP.put("S", "历年分数线");
        BUSINESS_INTENT_MAP.put("T", "招生政策");
        BUSINESS_INTENT_MAP.put("U", "报考指南");
        BUSINESS_INTENT_MAP.put("V", "校园信息");
        BUSINESS_INTENT_MAP.put("W", UNKNOWN_INTENT);
    }

    /**
     * 本地关键词兜底（用于RPC失败或结果不稳定时）
     */
    private static final Map<String, List<String>> INTENT_KEYWORDS = new HashMap<>();
    static {
        INTENT_KEYWORDS.put("专业信息", List.of("专业", "课程", "就业", "方向", "培养", "转专业"));
        INTENT_KEYWORDS.put("招生计划", List.of("招生计划", "招多少", "名额", "扩招", "缩招", "计划数"));
        INTENT_KEYWORDS.put("历年分数线", List.of("分数线", "位次", "录取线", "最低分", "投档线", "近三年"));
        INTENT_KEYWORDS.put("招生政策", List.of("政策", "规则", "条件", "章程", "加分", "录取办法"));
        INTENT_KEYWORDS.put("报考指南", List.of("报考", "志愿", "填报", "冲稳保", "建议", "怎么选"));
        INTENT_KEYWORDS.put("校园信息", List.of("住宿", "宿舍", "食堂", "奖学金", "校园", "交通", "环境"));
    }

    /**
     * 请求前置处理
     * 在发送AI请求前进行用户意图识别，将识别结果存入请求上下文
     *
     * @param chatClientRequest 聊天客户端请求对象
     * @param advisorChain 顾问链
     * @return 处理后的聊天客户端请求
     */
    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        // 1. 获取用户输入文本
        String userInput = chatClientRequest.prompt().getUserMessage().getText();
        log.debug("开始意图识别，用户输入: {}", userInput);

        if (!StringUtils.hasText(userInput)) {
            chatClientRequest.context().put("INTENT", UNKNOWN_INTENT);
            chatClientRequest.context().remove("intentSystemPrompt");
            ChatContext.setIntent(UNKNOWN_INTENT);
            return chatClientRequest;
        }

        try {
            // 2. 使用IntentRecognizerRpc识别用户意图
            String intent = recognizeIntentWithFallback(userInput);
            log.info("意图识别完成，识别结果: {}, 用户输入: {}", intent, userInput);

            // 3. 将识别结果存入请求上下文，供后续处理使用
            chatClientRequest.context().put("INTENT", intent);

            // 4. 基于意图拼装附加提示词，供后续 PromptMerge 融合
            String intentPrompt = resolveIntentPrompt(intent);
            if (StringUtils.hasText(intentPrompt)) {
                chatClientRequest.context().put("intentSystemPrompt", intentPrompt);
            } else {
                chatClientRequest.context().remove("intentSystemPrompt");
            }

            // 5. 将识别结果存入 ChatContext，用于追问建议
            ChatContext.setIntent(intent);
            
        } catch (Exception e) {
            log.error("意图识别失败，用户输入: {}, 错误信息: {}", userInput, e.getMessage(), e);
            // 识别失败时设置为未知意图
            chatClientRequest.context().put("INTENT", UNKNOWN_INTENT);
            chatClientRequest.context().remove("intentSystemPrompt");
            ChatContext.setIntent(UNKNOWN_INTENT);
        }

        return chatClientRequest;
    }

    /**
     * 使用远程服务识别用户意图
     * 调用IntentRecognizerRpc服务，根据用户输入和业务意图映射表进行意图分类
     * 
     * @param userInput 用户输入文本
     * @return 识别出的意图类别（中文名称）
     */
    private String recognizeIntent(String userInput) {
        log.debug("调用意图识别服务，输入长度: {} 字符", userInput.length());
        return intentRecognizerRpc.getIntent(BUSINESS_INTENT_MAP, userInput);
    }

    private String recognizeIntentWithFallback(String userInput) {
        String localIntent = detectIntentByKeyword(userInput);
        try {
            String rpcIntent = normalizeIntent(recognizeIntent(userInput));
            if (!UNKNOWN_INTENT.equals(rpcIntent)) {
                return rpcIntent;
            }
        } catch (Exception e) {
            log.debug("远程意图识别异常，使用本地兜底: {}", e.getMessage());
        }
        return localIntent;
    }

    private String detectIntentByKeyword(String userInput) {
        if (!StringUtils.hasText(userInput)) {
            return UNKNOWN_INTENT;
        }
        String normalized = userInput.replaceAll("\\s+", "").toLowerCase();
        for (Map.Entry<String, List<String>> entry : INTENT_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (normalized.contains(keyword.toLowerCase())) {
                    return entry.getKey();
                }
            }
        }
        return UNKNOWN_INTENT;
    }

    private String normalizeIntent(String rawIntent) {
        if (!StringUtils.hasText(rawIntent)) {
            return UNKNOWN_INTENT;
        }
        String intent = rawIntent.trim();
        if (BUSINESS_INTENT_MAP.containsKey(intent)) {
            return BUSINESS_INTENT_MAP.get(intent);
        }
        if (BUSINESS_INTENT_MAP.containsValue(intent)) {
            return intent;
        }
        if ("未知".equalsIgnoreCase(intent) || "other".equalsIgnoreCase(intent) || "others".equalsIgnoreCase(intent)) {
            return UNKNOWN_INTENT;
        }
        return UNKNOWN_INTENT;
    }

    private String resolveIntentPrompt(String intent) {
        boolean enabled = Boolean.parseBoolean(sysConfigService.getConfigValue(KEY_INTENT_PROMPT_ENABLED));
        if (!enabled) {
            return null;
        }

        String safeIntent = StringUtils.hasText(intent) ? intent.trim() : UNKNOWN_INTENT;
        Map<String, String> promptMap = loadIntentPromptMap();
        String prompt = promptMap.get(safeIntent);
        if (!StringUtils.hasText(prompt)) {
            prompt = promptMap.get(UNKNOWN_INTENT);
        }
        if (!StringUtils.hasText(prompt)) {
            return null;
        }
        return "【当前用户意图】" + safeIntent + "\n" + prompt.trim();
    }

    private Map<String, String> loadIntentPromptMap() {
        Map<String, String> defaults = defaultIntentPromptMap();
        try {
            String raw = sysConfigService.getConfigValue(KEY_INTENT_PROMPT_MAP);
            if (!StringUtils.hasText(raw)) {
                return defaults;
            }
            Map<String, String> configured = GSON.fromJson(raw, MAP_TYPE);
            if (configured == null || configured.isEmpty()) {
                return defaults;
            }
            for (Map.Entry<String, String> entry : configured.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                    defaults.put(key.trim(), value.trim());
                }
            }
            return defaults;
        } catch (Exception ignored) {
            return defaults;
        }
    }

    private Map<String, String> defaultIntentPromptMap() {
        Map<String, String> map = new HashMap<>();
        map.put("专业信息", "回答时优先覆盖专业特点、核心课程、培养方向与就业去向，建议给出对比信息。");
        map.put("招生计划", "回答时优先给出招生人数、省份/科类分布、专业计划变化，并注明年份。");
        map.put("历年分数线", "回答时优先提供近三年分数线和位次区间，并给出报考风险判断。");
        map.put("招生政策", "回答时优先说明政策条款、适用范围和时间节点，不确定信息需提示以官方公告为准。");
        map.put("报考指南", "回答时优先给出可执行步骤和分层建议（冲/稳/保），减少泛泛描述。");
        map.put("校园信息", "回答时优先覆盖学习生活、住宿、奖助体系和校园资源，突出用户决策相关信息。");
        map.put(UNKNOWN_INTENT, "若意图不清晰，请先澄清问题，再给出可供选择的追问方向。");
        return map;
    }

    /**
     * 同步调用响应处理
     * 处理同步AI响应，先执行前置意图识别处理，再继续调用链
     *
     * @param chatClientRequest 聊天客户端请求
     * @param callAdvisorChain 调用顾问链
     * @return 聊天客户端响应
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        log.debug("执行同步调用意图识别处理");
        ChatClientRequest processedRequest = before(chatClientRequest, callAdvisorChain);
        return callAdvisorChain.nextCall(processedRequest);
    }

    /**
     * 流式响应处理
     * 处理流式AI响应，先执行前置意图识别处理，再继续调用链
     *
     * @param chatClientRequest 聊天客户端请求
     * @param streamAdvisorChain 流式顾问链
     * @return 流式聊天客户端响应
     */
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        log.debug("执行流式调用意图识别处理");
        ChatClientRequest processedRequest = before(chatClientRequest, streamAdvisorChain);
        return streamAdvisorChain.nextStream(processedRequest);
    }

    /**
     * 响应后置处理
     * 在AI响应返回后进行的处理（当前实现为直接返回原响应）
     *
     * @param chatClientResponse 聊天客户端响应
     * @param advisorChain 顾问链
     * @return 处理后的聊天客户端响应
     */
    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        log.debug("执行意图识别响应后置处理");
        return chatClientResponse;
    }

    /**
     * 获取顾问执行顺序
     * 数字越小，执行优先级越高
     * 当前设置为1，在敏感词过滤(0)之后、问答增强(2)之前执行
     *
     * @return 顾问顺序值
     */
    @Override
    public int getOrder() {
        return 1;
    }
}
