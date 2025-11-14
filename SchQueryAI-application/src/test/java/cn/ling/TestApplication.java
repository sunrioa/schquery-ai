package cn.ling;

import cn.ling.rpc.RerankRpc;
import cn.ling.service.SegmentationWordsService;
import cn.ling.service.SensitiveWordsService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.io.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@SpringBootTest
class TestApplication {

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
