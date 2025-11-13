package cn.ling.advisor;

import cn.ling.utils.WordsFilterUtils;
import jakarta.annotation.Resource;
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

@Component
public class SensitiveFilterAdvisor implements BaseAdvisor {

    @Resource
    private WordsFilterUtils wordsFilterUtils;

    private static final String SENSITIVE_PROMPT = "哎呀，你的输入中可能包含不适合的敏感内容哦～（{param}） 麻烦调整一下表述再提交呀，谢谢配合！";

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        List<String> sensitiveWords = wordsFilterUtils.doFilter(chatClientRequest.prompt().getUserMessage().getText());
        if (!sensitiveWords.isEmpty()) {
            chatClientRequest.context().put("sensitiveWords",String.join("、", sensitiveWords));
        }
        return chatClientRequest;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        ChatClientRequest filteredRequest = this.before(request, chain);
        Object sensitiveWordsObj = request.context().get("sensitiveWords");
        return sensitiveWordsObj==null?
                chain.nextStream(filteredRequest).map(response -> this.after(response, chain))
                :
                Flux.just(
                        ChatClientResponse.builder()
                                .chatResponse(
                                        ChatResponse.builder()
                                                .generations(
                                                        List.of(
                                                                new Generation(
                                                                        new AssistantMessage(
                                                                                SENSITIVE_PROMPT.replace("{param}", sensitiveWordsObj.toString())
                                                                        )
                                                                )
                                                        )
                                                )
                                                .build()
                                ).build());
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientRequest filteredRequest = this.before(request, chain);
        Object sensitiveWordsObj = request.context().get("sensitiveWords");
        return sensitiveWordsObj==null?
                after(chain.nextCall(filteredRequest), chain)
                :
                ChatClientResponse.builder()
                        .chatResponse(
                                ChatResponse.builder()
                                        .generations(
                                                List.of(
                                                        new Generation(
                                                                new AssistantMessage(
                                                                        SENSITIVE_PROMPT.replace("{param}", sensitiveWordsObj.toString())
                                                                )
                                                        )
                                                )
                                        )
                                        .build()

                        ).build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}