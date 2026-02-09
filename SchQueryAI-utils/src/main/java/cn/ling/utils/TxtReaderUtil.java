package cn.ling.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * TXT文档读取工具类
 * 提供纯文本文件的读取功能，支持多种字符编码
 * 使用原生IO流进行文件读取操作
 */
@Slf4j
public class TxtReaderUtil {

    /**
     * 从上传的TXT文件中读取文本内容
     * 默认使用UTF-8编码读取文件内容
     *
     * @param file 上传的TXT文件对象
     * @return 文件中的文本内容；如果读取失败返回null
     * @throws RuntimeException 当文件读取过程发生异常时抛出
     */
    public static String read(MultipartFile file) {
        return read(file, StandardCharsets.UTF_8);
    }

    /**
     * 从上传的TXT文件中读取文本内容
     * 支持指定字符编码进行读取
     *
     * @param file 上传的TXT文件对象
     * @param charset 指定的字符编码
     * @return 文件中的文本内容；如果读取失败返回null
     * @throws RuntimeException 当文件读取过程发生异常时抛出
     */
    public static String read(MultipartFile file, Charset charset) {
        log.debug("开始读取TXT文件 - 文件名: {}, 指定编码: {}",
                file.getOriginalFilename(), charset.name());

        try {
            // 参数验证：检查文件是否存在且不为空
            if (file == null || file.isEmpty()) {
                log.warn("TXT文件为空或不存在，读取失败");
                return null;
            }

            // 使用指定编码读取文件内容
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(file.getInputStream(), charset))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (content.length() > 0) {
                        content.append(System.lineSeparator());
                    }
                    content.append(line);
                }
            }

            log.info("TXT文件读取成功 - 文件名: {}, 内容长度: {}字符",
                    file.getOriginalFilename(), content.length());

            return content.toString();

        } catch (IOException e) {
            log.error("TXT文件读取失败 - 文件名: {}, 错误信息: {}",
                    file.getOriginalFilename(), e.getMessage(), e);
            throw new RuntimeException("TXT文件读取失败: " + e.getMessage(), e);
        }
    }
}
