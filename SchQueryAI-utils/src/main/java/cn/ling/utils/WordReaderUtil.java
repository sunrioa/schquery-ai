package cn.ling.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Word文档读取工具类
 * 提供Word文件（.doc/.docx）的文本内容提取功能
 * 基于Apache POI库实现Word内容读取，支持.doc和.docx两种格式
 */
@Slf4j
public class WordReaderUtil {

    /**
     * 从上传的Word文件中提取文本内容
     * 自动识别文档格式（.doc或.docx）并提取文本
     *
     * @param file 上传的Word文件对象
     * @return Word中的文本内容；如果读取失败返回null
     * @throws RuntimeException 当文件读取过程发生异常时抛出
     */
    public static String read(MultipartFile file) {
        log.debug("开始读取Word文件 - 文件名: {}, 文件大小: {}字节",
                file.getOriginalFilename(), file.getSize());

        try {
            // 参数验证：检查文件是否存在且不为空
            if (file == null || file.isEmpty()) {
                log.warn("Word文件为空或不存在，读取失败");
                return null;
            }

            String filename = file.getOriginalFilename();
            if (filename == null) {
                log.warn("文件名为空，无法确定文件类型");
                return null;
            }

            // 根据文件扩展名选择读取方式
            String text;
            if (filename.toLowerCase().endsWith(".docx")) {
                log.debug("检测到.docx格式，使用XWPF读取");
                text = readDocx(file);
            } else if (filename.toLowerCase().endsWith(".doc")) {
                log.debug("检测到.doc格式，使用HWPF读取");
                text = readDoc(file);
            } else {
                log.warn("不支持的Word文件格式: {}", filename);
                return null;
            }

            log.info("Word文件读取成功 - 文件名: {}, 提取文本长度: {}字符",
                    filename, text.length());

            return text;

        } catch (Exception e) {
            log.error("Word文件读取失败 - 文件名: {}, 错误信息: {}",
                    file.getOriginalFilename(), e.getMessage(), e);
            throw new RuntimeException("Word文件读取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 读取.docx格式的Word文档
     * 使用Apache POI的XWPF组件处理Office 2007+格式
     *
     * @param file 上传的Word文件对象
     * @return 文档中的文本内容
     * @throws IOException 当文件读取失败时抛出
     */
    private static String readDocx(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream();
             XWPFDocument document = new XWPFDocument(is);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {

            String text = extractor.getText();
            log.debug("DOCX文档内容提取完成 - 文本长度: {}字符", text.length());

            return text;
        }
    }

    /**
     * 读取.doc格式的Word文档
     * 使用Apache POI的HWPF组件处理Office 97-2003格式
     *
     * @param file 上传的Word文件对象
     * @return 文档中的文本内容
     * @throws IOException 当文件读取失败时抛出
     */
    private static String readDoc(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream();
             HWPFDocument document = new HWPFDocument(is);
             WordExtractor extractor = new WordExtractor(document)) {

            String text = extractor.getText();
            log.debug("DOC文档内容提取完成 - 文本长度: {}字符", text.length());

            return text;
        }
    }
}
