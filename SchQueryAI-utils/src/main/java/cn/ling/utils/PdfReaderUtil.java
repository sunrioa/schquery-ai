package cn.ling.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * PDF文档读取工具类
 * 提供PDF文件的文本内容提取功能
 * 基于Apache PDFBox库实现PDF文本提取
 */
@Slf4j
public class PdfReaderUtil {

    /**
     * 从上传的PDF文件中提取文本内容
     * 提取PDF中所有可识别的文本内容，按页顺序拼接
     *
     * @param file 上传的PDF文件对象
     * @return PDF中的文本内容；如果读取失败返回null
     * @throws RuntimeException 当文件读取过程发生异常时抛出
     */
    public static String read(MultipartFile file) {
        log.debug("开始读取PDF文件 - 文件名: {}, 文件大小: {}字节",
                file.getOriginalFilename(), file.getSize());

        try {
            // 参数验证：检查文件是否存在且不为空
            if (file == null || file.isEmpty()) {
                log.warn("PDF文件为空或不存在，读取失败");
                return null;
            }

            // 使用PDFBox加载PDF文档
            try (PDDocument document = PDDocument.load(file.getInputStream())) {

                int pageCount = document.getNumberOfPages();
                log.debug("PDF文档加载成功 - 总页数: {}", pageCount);

                // 创建PDF文本提取器
                PDFTextStripper textStripper = new PDFTextStripper();

                // 设置文本提取参数
                textStripper.setSortByPosition(true); // 按位置排序文本
                textStripper.setLineSeparator("\n"); // 设置行分隔符

                // 提取全部页面的文本内容
                String text = textStripper.getText(document);

                log.info("PDF文件读取成功 - 文件名: {}, 页数: {}, 提取文本长度: {}字符",
                        file.getOriginalFilename(), pageCount, text.length());

                return text;
            }

        } catch (IOException e) {
            log.error("PDF文件读取失败 - 文件名: {}, 错误信息: {}",
                    file.getOriginalFilename(), e.getMessage(), e);
            throw new RuntimeException("PDF文件读取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从上传的PDF文件中提取指定页范围的文本内容
     *
     * @param file 上传的PDF文件对象
     * @param startPage 起始页码（从1开始）
     * @param endPage 结束页码
     * @return 指定页范围的文本内容；如果读取失败返回null
     * @throws RuntimeException 当文件读取过程发生异常时抛出
     */
    public static String read(MultipartFile file, int startPage, int endPage) {
        log.debug("开始读取PDF文件指定页范围 - 文件名: {}, 起始页: {}, 结束页: {}",
                file.getOriginalFilename(), startPage, endPage);

        try {
            // 参数验证：检查文件是否存在且不为空
            if (file == null || file.isEmpty()) {
                log.warn("PDF文件为空或不存在，读取失败");
                return null;
            }

            // 使用PDFBox加载PDF文档
            try (PDDocument document = PDDocument.load(file.getInputStream())) {

                int totalPages = document.getNumberOfPages();

                // 验证页码范围
                if (startPage < 1 || endPage > totalPages || startPage > endPage) {
                    log.warn("指定的页码范围无效 - 总页数: {}, 请求范围: {}-{}",
                            totalPages, startPage, endPage);
                    throw new IllegalArgumentException("无效的页码范围");
                }

                // 创建PDF文本提取器并设置页码范围
                PDFTextStripper textStripper = new PDFTextStripper();
                textStripper.setSortByPosition(true);
                textStripper.setLineSeparator("\n");
                textStripper.setStartPage(startPage);
                textStripper.setEndPage(endPage);

                // 提取指定页范围的文本内容
                String text = textStripper.getText(document);

                log.info("PDF文件指定页范围读取成功 - 文件名: {}, 页范围: {}-{}, 提取文本长度: {}字符",
                        file.getOriginalFilename(), startPage, endPage, text.length());

                return text;
            }

        } catch (IOException e) {
            log.error("PDF文件读取失败 - 文件名: {}, 错误信息: {}",
                    file.getOriginalFilename(), e.getMessage(), e);
            throw new RuntimeException("PDF文件读取失败: " + e.getMessage(), e);
        }
    }
}
