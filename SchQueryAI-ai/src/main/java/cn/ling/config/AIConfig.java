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
 * 配置Spring AI相关组件，包括RAG检索增强、文本分割等功能
 * 支持基于向量数据库的智能问答和文档处理
 */
@Slf4j
@Configuration
public class AIConfig {
    
    /**
     * 配置向量检索请求参数
     * 定义向量相似度搜索的关键参数，用于控制RAG检索质量
     * 
     * @param topK 返回的最相似文档数量（从配置文件读取）
     * @param similarityThreshold 相似度阈值，低于此值的结果会被过滤（从配置文件读取）
     * @return SearchRequest检索请求对象
     */
    @Bean("searchRequest")
    public SearchRequest searchRequest(
            @Value("${spring.ai.openai.embedding.options.topK}") Integer topK,
            @Value("${spring.ai.openai.embedding.options.similarityThreshold}") Double similarityThreshold
    ){
        log.info("配置向量检索请求参数 - TopK: {}, 相似度阈值: {}", topK, similarityThreshold);
        
        SearchRequest searchRequest = SearchRequest.builder()
                .topK(topK)  // 返回最相似的K个结果
                .similarityThreshold(similarityThreshold)  // 设置相似度阈值
                .build();
        
        log.debug("向量检索请求配置完成");
        return searchRequest;
    }

    /**
     * 配置问答增强顾问
     * 基于向量数据库的RAG（Retrieval-Augmented Generation）功能
     * 自动从向量库中检索相关文档，增强AI回答的准确性和上下文相关性
     * 
     * @param qdrantVectorStore Qdrant向量存储实例
     * @param searchRequest 检索请求配置
     * @return Advisor问答增强顾问实例
     */
    @Bean("questionAnswerAdvisor")
    public Advisor questionAnswerAdvisor(
            @Qualifier("qdrantVectorStore") VectorStore qdrantVectorStore,
            @Qualifier("searchRequest") SearchRequest searchRequest
    ){
        log.info("开始配置问答增强顾问");
        
        Advisor advisor = QuestionAnswerAdvisor.builder(qdrantVectorStore)
                .searchRequest(searchRequest)  // 设置检索参数
                .order(2)  // 设置执行顺序，在敏感词过滤(0)和意图识别(1)之后执行
                .build();
        
        log.info("问答增强顾问配置完成，执行顺序: 2");
        return advisor;
    }

    /**
     * 配置Token文本分割器
     * 基于Token数量对文本进行分割，确保每个分块不超过模型的Token限制
     * 适用于需要精确控制Token数量的场景
     * 
     * @return TokenTextSplitter Token文本分割器实例
     */
    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        log.info("配置Token文本分割器 - 分块大小: 300, 最小字符数: 100");
        
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(300)  // 缩小单段最大Token数，强制分割长文本
                .withMinChunkSizeChars(100)  // 最小字符数，避免过短片段
                .withMinChunkLengthToEmbed(10)  // 最小嵌入长度
                .withKeepSeparator(true)  // 保留分隔符
                .build();
        
        log.debug("Token文本分割器配置完成");
        return splitter;
    }

    /**
     * 配置自定义文本分割工具
     * 支持多种中文分隔符，适配中文文档结构（章节、条款、列表等）
     * 支持分块重叠，避免上下文信息丢失
     * 
     * @return TextSplitterUtils自定义文本分割工具实例
     */
    @Bean
    public TextSplitterUtils textSplitterUtils(){
        log.info("配置自定义文本分割工具 - 分块大小: 600, 重叠大小: 100");
        
        TextSplitterUtils textSplitter = new TextSplitterUtils.Builder()
                // 定义多层级分隔符：段落、章节、条款、列表、句号等
                .separators(Arrays.asList(
                        "\n\n",  // 段落分隔
                        "第.*章", "第.*条",  // 章节条款
                        "一、", "二、", "三、", "四、", "五、", "六、", "七、", "八、", "九、", "十、",  // 中文序号
                        "（一）", "（二）", "（三）", "（四）", "（五）", "（六）", "（七）", "（八）", "（九）", "（十）",  // 括号序号
                        "1. ", "2. ", "3. ", "4. ", "5. ", "6. ", "7. ", "8. ", "9. ", "10. ",  // 数字序号
                        "。", "；", "！", "？"  // 中文标点符号
                ))
                .chunkSize(600)  // 单个分块最大字符数
                .chunkOverlap(100)  // 分块之间的重叠字符数，保证上下文连贯性
                .lengthFunction(TextSplitterUtils.defaultLengthFunction())  // 使用默认长度计算函数
                .build();
        
        log.debug("自定义文本分割工具配置完成，支持的分隔符数量: {}", textSplitter.toString());
        return textSplitter;
    }
}