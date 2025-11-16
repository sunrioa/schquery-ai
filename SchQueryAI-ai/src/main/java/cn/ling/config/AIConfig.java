package cn.ling.config;

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
    public TokenTextSplitter tokenTextSplitter(){
        return new TokenTextSplitter();
    }

}