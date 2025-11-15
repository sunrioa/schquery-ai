package cn.ling;

import cn.ling.rpc.RerankRpc;
import cn.ling.service.SegmentationWordsService;
import cn.ling.service.SensitiveWordsService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.io.*;
import java.util.*;

@Slf4j
@SpringBootTest
class TestApplication {

    @Resource(name = "qdrantVectorStore")
    VectorStore vectorStore;

    @Resource
    OpenAiChatModel openAiChatModel;

    @Test
    public void test() {
        QuestionAnswerAdvisor questionAnswerAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(
                        SearchRequest.builder()
                                .similarityThreshold(0.8d)
                                .topK(6)
                                .build()
                )
                .build();

        ChatClient chatClient = ChatClient.builder(openAiChatModel)
                .defaultAdvisors(questionAnswerAdvisor)
                .build();

        ChatClient.CallResponseSpec call = chatClient.prompt()
                .user("Funasr和PP-StructureV3你知道吗？")
                .advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, "jianli == 'zzl'"))
                .call();

        System.out.println("模型回答：" + call.chatResponse().getResult().getOutput().getText());
    }



    @Test
    void demo() {
        // 1. 定义要处理的文本内容
        String input = """
                项目背景：公司主营建筑行业信息化软件，长期受困于海量非结构化数据工程图纸、合同扫描件、项目录音的处理，传统人工方式效率低下、成本高昂。我参与了公司 大模型应用开发 ，旨在通过AI技术实现数据的智能转化，最终封装为服务赋能全线业务产品，降低人工成本并提升数据流转效率。
                主要工作：
                1.搭建基于 FunASR模型 的 语音识别 服务。通过 WebSocket客户端 与模型交互，使用 Redis Zset 解决了 消息乱序 并实现 断点续传 。消息队列 异步触发 后续LLM总结。将1小时音频的处理时长控制在20分钟内，准确率达90%。
                2.开发基于 PP-StructureV3模型 的 版面识别 服务。通过 Forest客户端 远程调用Flask后端，集成 熔断、降级与限流 机制保证服务链路的健壮性。实现了扫描件PDF的结构化提取，为多个关键上游业务提供了数据支撑。
                3.参与设计并实现基于 Spring AI 的 Agent 流程中枢，构建 数据库驱动的动态组装 机制：从数据库读取提示词、工具链及模型参数等配置，在系统运行时自动组装为完整ChatClient实例，提升Agent服务的灵活性与可扩展性。
                """;

        Document document = new Document(input,
                Map.of("jianli", "zzl")
        );


        // 4. 将Document存入Qdrant向量数据库
        vectorStore.add(List.of(document));  // add方法接收Document列表

        // 验证：可以查询一下是否存入成功（可选）
        List<Document> retrievedDocs = vectorStore.similaritySearch("建筑行业信息化");
        System.out.println("检索到的文档数量：" + retrievedDocs.size());
        retrievedDocs.forEach(doc -> System.out.println("文档内容：" + doc.getText()));
    }



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

    @Resource
    private ChatClient chatClient;

    @Test
    public void intent() {
        String userInput = "学校里面有多少个宿舍楼？环境怎么样";
        System.out.println(chatClient.prompt(userInput).call().chatClientResponse());
    }

    @Resource
    RerankRpc rpc;

    @Test
    public void testRpc(){
        RerankRpc.RerankRequest request = new RerankRpc.RerankRequest();
        request.setModel("qwen3-rerank");
        request.setInput(new RerankRpc.RerankRequest.Input("什么是文本重排序模型？",new String[]{
                "文本排序模型广泛用于搜索引擎和推荐系统中，它们根据文本相关性对候选文本进行排序",
                "量子计算是计算科学的一个前沿领域",
                "预训练语言模型的发展给文本排序模型带来了新的进展"
        }));
        request.setParameters(new RerankRpc.RerankRequest.Parameters(true,2,"Given a web search query, retrieve relevant passages that answer the query."));

        RerankRpc.RerankResponse response = rpc.rerank(request);
        System.out.println(response);
    }

    @Autowired
    SensitiveWordsService sensitiveWordsService;

    @Autowired
    SegmentationWordsService segmentationWordsService;

    private static final String FILE_PATH = "src/main/resources/sensitive_words";

    @Test
    public void loadData(){
        try (FileInputStream fileInputStream = new FileInputStream(FILE_PATH);
             InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {

            List<String> words=new ArrayList<>();
            while (bufferedReader.ready())  {
                byte[] decode = Base64.getDecoder().decode(bufferedReader.readLine());
                String decodedLine = new String(decode);

                words.add(decodedLine);
            }
            sensitiveWordsService.loadData(words);
            segmentationWordsService.loadData(words);
        } catch (IOException e) {
            log.error("加载敏感词失败!");
        }
    }

    /**
     * 将加密的敏感词文件（Base64编码）解码为原始文本并保存到新文件
     */
    @Test
    public void decodeSensitiveWordsToRawFile() {
        // 加密文件路径（原敏感词文件）
        String encodedFilePath = "src/main/resources/sensitive_words";
        // 解密后保存的文件路径
        String rawFilePath = "src/main/resources/sensitive_words_raw";

        try (
                // 读取加密文件
                FileInputStream fis = new FileInputStream(encodedFilePath);
                InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
                BufferedReader br = new BufferedReader(isr);

                // 写入解密后的文件
                FileOutputStream fos = new FileOutputStream(rawFilePath);
                OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
                BufferedWriter bw = new BufferedWriter(osw)
        ) {
            String encodedLine;
            int lineCount = 0;
            // 逐行解码并写入新文件
            while ((encodedLine = br.readLine()) != null) {
                // 跳过空行
                if (encodedLine.trim().isEmpty()) {
                    continue;
                }
                // Base64解码
                byte[] decodedBytes = Base64.getDecoder().decode(encodedLine);
                String rawLine = new String(decodedBytes, "UTF-8");
                // 写入原始文本
                bw.write(rawLine);
                bw.newLine(); // 保持行结构
                lineCount++;
            }
            log.info("敏感词解密完成，共处理 {} 行，已保存至 {}", lineCount, rawFilePath);
        } catch (IOException e) {
            log.error("敏感词解密或保存失败！", e);
        }
    }

}
