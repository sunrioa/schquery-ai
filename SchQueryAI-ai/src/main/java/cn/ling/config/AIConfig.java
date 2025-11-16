package cn.ling.config;

import cn.ling.advisor.IntentRecognizerAdvisor;
import cn.ling.advisor.SensitiveFilterAdvisor;
import cn.ling.vector.CustomQdrantVectorStore;
import io.qdrant.client.QdrantClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
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
    /**
     * 配置OpenAI API
     * 创建OpenAI API实例，用于与AI模型服务进行通信
     *
     * @param baseUrl API基础地址（从配置文件读取）
     * @param apiKey API访问密钥（从配置文件读取）
     * @return OpenAiApi实例
     */
    @Bean
    public OpenAiApi openAiApi(
            @Value("${spring.ai.openai.base-url}") String baseUrl,
            @Value("${spring.ai.openai.api-key}") String apiKey ) {
        log.info("开始配置OpenAI API，基础地址: {}", baseUrl);

        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

        log.info("OpenAI API配置完成，基础地址: {}", baseUrl);
        return openAiApi;
    }

    /**
     * 配置OpenAI聊天选项
     * 设置AI聊天模型的基本参数和选项
     *
     * @param model AI模型名称（从配置文件读取）
     * @return OpenAiChatOptions配置对象
     */
    @Bean
    public OpenAiChatOptions openAiChatOptions(@Value("${spring.ai.openai.chat.options.model}") String model){
        log.info("开始配置OpenAI聊天选项，模型: {}", model);

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .build();

        log.info("OpenAI聊天选项配置完成，使用模型: {}", model);
        return options;
    }

    @Bean
    public OpenAiEmbeddingOptions openAiEmbeddingOptions(
            @Value("${spring.ai.openai.embedding.options.model}") String model,
            @Value("${spring.ai.openai.embedding.options.dimensions}") Integer dimensions){
        return OpenAiEmbeddingOptions.builder()
                .model(model)
                .dimensions(dimensions)
                .build();
    }

    /**
     * 配置OpenAI聊天模型
     * 创建聊天模型实例，集成API和聊天选项
     *
     * @param openAiApi OpenAI API实例
     * @param openAiChatOptions 聊天选项配置
     * @return OpenAiChatModel聊天模型实例
     */
    @Bean
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi, OpenAiChatOptions openAiChatOptions){
        log.info("开始配置OpenAI聊天模型");

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(openAiChatOptions)
                .build();

        log.info("OpenAI聊天模型配置完成");
        return chatModel;
    }

    @Bean
    public EmbeddingModel embeddingModel(OpenAiApi openAiApi, OpenAiEmbeddingOptions openAiEmbeddingOptions) {
        return new OpenAiEmbeddingModel(
                openAiApi,
                MetadataMode.ALL,
                openAiEmbeddingOptions
        );
    }

    @Bean
    public VectorStore qdrantVectorStore(
            QdrantClient qdrantClient,
            EmbeddingModel embeddingModel,
            @Value("${spring.ai.vectorstore.qdrant.collection-name}") String collectionName,
            @Value("${spring.ai.vectorstore.qdrant.initialize-schema}") Boolean initializeSchema
    ) {
        return QdrantVectorStore.builder(qdrantClient, embeddingModel)
                .batchingStrategy(new TokenCountBatchingStrategy())
                .collectionName(collectionName)
                .initializeSchema(initializeSchema)
                .build();
    }
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

    // SchQueryAi系统提示词 - 高校智能招生助手人设
    String systemPrompt =
            """
            你是SchQueryAi，一个高校智能招生助手。
            主要职责：
            1. 提供招生政策、专业介绍、校园生活信息
            2. 解答报考流程、录取规则、学费资助问题
            3. 介绍特色专业、师资力量、校园设施
            4. 提供志愿填报建议和职业规划指导

            重要提醒：
//            - 绝对不要提及任何第三方模型或公司
            - 自然介绍自己是SchQueryAi高校智能招生助手
            """;

    /**
     * 配置OpenAI聊天客户端
     * 创建ChatClient实例，设置系统提示词和敏感词过滤器
     *
     * @param openAiChatModel OpenAI聊天模型实例
     * @return 配置好的ChatClient聊天客户端
     */
    @Bean
    public ChatClient openAiChatClient(
            OpenAiChatModel openAiChatModel,
            @Qualifier("questionAnswerAdvisor") Advisor questionAnswerAdvisor,
            SensitiveFilterAdvisor sensitiveFilterAdvisor,
            IntentRecognizerAdvisor intentRecognizerAdvisor
    ) {
        log.info("开始配置OpenAI聊天客户端");

        return ChatClient.builder(openAiChatModel)
                .defaultSystem(systemPrompt)
                .defaultAdvisors(
                        questionAnswerAdvisor,
                        intentRecognizerAdvisor,
                        sensitiveFilterAdvisor
                )
                .build();
    }

    @Bean
    public TokenTextSplitter tokenTextSplitter(){
        return new TokenTextSplitter();
    }

    @Bean
    public CustomQdrantVectorStore customQdrantVectorStore(
            QdrantClient qdrantClient,
            EmbeddingModel embeddingModel
    ){
        return new CustomQdrantVectorStore.Builder(qdrantClient, embeddingModel).build();
    }

}
