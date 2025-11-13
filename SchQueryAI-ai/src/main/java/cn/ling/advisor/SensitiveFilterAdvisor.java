package cn.ling.advisor;

import cn.ling.utils.WordsFilterUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import java.util.List;

/**
 * 敏感词过滤顾问
 * Spring AI 顾问模式实现，用于在AI对话过程中进行敏感词过滤
 * 在请求发送前检测用户输入中的敏感词，并返回友好的提示信息
 */
@Slf4j
@Component
public class SensitiveFilterAdvisor implements BaseAdvisor {

    @Resource
    private WordsFilterUtils wordsFilterUtils;

    /**
     * 敏感词提示消息模板
     * 当检测到敏感词时返回给用户的友好提示信息
     */
    private static final String SENSITIVE_PROMPT = "哎呀，你的输入中可能包含不适合的敏感内容哦～（{param}） 麻烦调整一下表述再提交呀，谢谢配合！";

    /**
     * 请求前置处理
     * 在发送AI请求前进行敏感词检测，将检测结果存入请求上下文
     *
     * @param chatClientRequest 聊天客户端请求对象
     * @param advisorChain 顾问链
     * @return 处理后的聊天客户端请求
     */
    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        // 获取用户输入文本
        String userInput = chatClientRequest.prompt().getUserMessage().getText();
        log.debug("开始敏感词检测，用户输入长度: {} 字符", userInput.length());

        // 执行敏感词过滤
        List<String> sensitiveWords = wordsFilterUtils.doFilter(userInput);

        if (!sensitiveWords.isEmpty()) {
            // 发现敏感词，记录并存储到请求上下文
            String sensitiveWordsStr = String.join("、", sensitiveWords);
            chatClientRequest.context().put("sensitiveWords", sensitiveWordsStr);
            log.info("检测到敏感词: {}, 输入长度: {} 字符", sensitiveWordsStr, userInput.length());
        } else {
            log.debug("未检测到敏感词，允许继续处理");
        }

        return chatClientRequest;
    }

    /**
     * 流式响应处理
     * 处理流式AI响应，根据敏感词检测结果决定是否返回AI响应或敏感词提示
     *
     * @param request 聊天客户端请求
     * @param chain 流式顾问链
     * @return 流式聊天客户端响应
     */
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        // 执行前置处理（敏感词检测）
        ChatClientRequest filteredRequest = before(request, chain);
        Object sensitiveWordsObj = request.context().get("sensitiveWords");

        if (sensitiveWordsObj == null) {
            // 无敏感词，正常处理AI响应
            log.debug("流式响应：无敏感词，继续处理AI响应");
            return chain.nextStream(filteredRequest).map(response -> after(response, chain));
        } else {
            // 检测到敏感词，返回敏感词提示
            String sensitiveWords = sensitiveWordsObj.toString();
            String promptMessage = SENSITIVE_PROMPT.replace("{param}", sensitiveWords);

            log.info("流式响应：检测到敏感词，返回提示信息，敏感词: {}", sensitiveWords);

            return Flux.just(
                    ChatClientResponse.builder()
                            .chatResponse(
                                    ChatResponse.builder()
                                            .generations(
                                                    List.of(
                                                            new Generation(
                                                                    new AssistantMessage(promptMessage)
                                                            )
                                                    )
                                            )
                                            .build()
                            ).build());
        }
    }

    /**
     * 同步调用响应处理
     * 处理同步AI响应，根据敏感词检测结果决定是否返回AI响应或敏感词提示
     *
     * @param request 聊天客户端请求
     * @param chain 调用顾问链
     * @return 聊天客户端响应
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        // 执行前置处理（敏感词检测）
        ChatClientRequest filteredRequest = before(request, chain);
        Object sensitiveWordsObj = request.context().get("sensitiveWords");

        if (sensitiveWordsObj == null) {
            // 无敏感词，正常处理AI响应
            log.debug("同步响应：无敏感词，继续处理AI响应");
            return after(chain.nextCall(filteredRequest), chain);
        } else {
            // 检测到敏感词，返回敏感词提示
            String sensitiveWords = sensitiveWordsObj.toString();
            String promptMessage = SENSITIVE_PROMPT.replace("{param}", sensitiveWords);

            log.info("同步响应：检测到敏感词，返回提示信息，敏感词: {}", sensitiveWords);

            return ChatClientResponse.builder()
                    .chatResponse(
                            ChatResponse.builder()
                                    .generations(
                                            List.of(
                                                    new Generation(
                                                            new AssistantMessage(promptMessage)
                                                    )
                                            )
                                    )
                                    .build()
                    ).build();
        }
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
        log.debug("执行响应后置处理");
        return chatClientResponse;
    }

    /**
     * 获取顾问执行顺序
     * 数字越小，执行优先级越高
     *
     * @return 顾问顺序值
     */
    @Override
    public int getOrder() {
        return 0;
    }
}