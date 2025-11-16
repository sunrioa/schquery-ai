package cn.ling.config;

import cn.ling.utils.TextSplitterUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Arrays;

/**
 * AI功能配置类
 * 配置Spring AI相关组件，包括OpenAI API、聊天模型和聊天客户端
 * 集成敏感词过滤功能，确保AI对话的安全性
 */
@Slf4j
@Configuration
public class AIConfig {
    @Bean("searchRequest")
    public SearchRequest searchRequest(
            @Value("${spring.ai.openai.embedding.options.topK}") Integer topK,
            @Value("${spring.ai.openai.embedding.options.similarityThreshold}") Double similarityThreshold
    ){
        return SearchRequest.builder()
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();
    }

    @Bean("questionAnswerAdvisor")
    public Advisor questionAnswerAdvisor(
            @Qualifier("qdrantVectorStore") VectorStore qdrantVectorStore,
            @Qualifier("searchRequest") SearchRequest searchRequest
    ){
        return QuestionAnswerAdvisor.builder(qdrantVectorStore)
                .searchRequest(searchRequest)
                .order(2)
                .build();
    }

    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return TokenTextSplitter.builder()
                .withChunkSize(300)  // 缩小单段最大Token数，强制分割长文本
                .withMinChunkSizeChars(100)  // 最小字符数，避免过短片段
                .withMinChunkLengthToEmbed(10)
                .withKeepSeparator(true)
                .build();
    }

    @Bean
    public TextSplitterUtils textSplitterUtils(){
        return new TextSplitterUtils.Builder()
                .separators(Arrays.asList("\n\n", "第.*章", "第.*条", "一、", "二、", "三、", "四、", "五、", "六、", "七、", "八、", "九、", "十、", "（一）", "（二）", "（三）", "（四）", "（五）", "（六）", "（七）", "（八）", "（九）", "（十）", "1. ", "2. ", "3. ", "4. ", "5. ", "6. ", "7. ", "8. ", "9. ", "10. ", "。", "；", "！", "？"))
                .chunkSize(600)
                .chunkOverlap(100)
                .lengthFunction(TextSplitterUtils.defaultLengthFunction())
                .build();
    }

}