package cn.ling;

import cn.ling.service.SegmentationWordsService;
import cn.ling.service.SensitiveWordsService;
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
