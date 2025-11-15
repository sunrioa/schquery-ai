package cn.ling.advisor;

import cn.ling.rpc.IntentRecognizerRpc;
import jakarta.annotation.Resource;
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

@Component
public class IntentRecognizerAdvisor implements BaseAdvisor {

    @Resource
    private IntentRecognizerRpc intentRecognizerRpc;

    // 自定义意图映射（与业务相关的意图）
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

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        // 1. 获取用户输入
        String userInput = chatClientRequest.prompt().getUserMessage().getText();

        // 2. 使用IntentRecognizerRpc识别意图
        String intent = recognizeIntent(userInput);

        // 3. 存入上下文
        chatClientRequest.context().put("INTENT", intent);
        return chatClientRequest;
    }

    /**
     * 使用IntentRecognizerRpc识别意图
     */
    private String recognizeIntent(String userInput) {
        return intentRecognizerRpc.getIntent(BUSINESS_INTENT_MAP,userInput);
    }


    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        ChatClientRequest processedRequest = before(chatClientRequest, callAdvisorChain);
        return callAdvisorChain.nextCall(processedRequest);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        ChatClientRequest processedRequest = before(chatClientRequest, streamAdvisorChain);
        return streamAdvisorChain.nextStream(processedRequest);
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return 1;
    }
}