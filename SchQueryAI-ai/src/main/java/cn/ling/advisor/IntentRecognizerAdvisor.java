package cn.ling.advisor;

import cn.ling.context.ChatContext;
import cn.ling.rpc.IntentRecognizerRpc;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import java.util.Map;
import java.util.HashMap;

/**
 * 意图识别顾问
 * Spring AI 顾问模式实现，用于在AI对话过程中进行用户意图识别
 * 通过远程调用意图识别服务，将用户输入分类为不同的业务意图类别
 * 识别结果会存储在请求上下文中，供后续处理流程使用
 */
@Slf4j
@Component
public class IntentRecognizerAdvisor implements BaseAdvisor {

    /**
     * 意图识别远程调用客户端
     * 用于调用外部意图识别服务API
     */
    @Resource
    private IntentRecognizerRpc intentRecognizerRpc;

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
        BUSINESS_INTENT_MAP.put("W", "UNKNOWN");
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

        try {
            // 2. 使用IntentRecognizerRpc识别用户意图
            String intent = recognizeIntent(userInput);
            if (intent == null || intent.isBlank()) {
                intent = "UNKNOWN";
            }
            log.info("意图识别完成，识别结果: {}, 用户输入: {}", intent, userInput);

            // 3. 将识别结果存入请求上下文，供后续处理使用
            chatClientRequest.context().put("INTENT", intent);

            // 4. 将识别结果存入 ChatContext，用于追问建议
            ChatContext.setIntent(intent);
            
        } catch (Exception e) {
            log.error("意图识别失败，用户输入: {}, 错误信息: {}", userInput, e.getMessage(), e);
            // 识别失败时设置为未知意图
            chatClientRequest.context().put("INTENT", "UNKNOWN");
            ChatContext.setIntent("UNKNOWN");
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
