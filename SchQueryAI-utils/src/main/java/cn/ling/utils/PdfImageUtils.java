package cn.ling.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * JavaCV PDF 截图工具类
 * 将 PDF 文件转换为图片，支持多页 PDF 和 Base64 编码
 * 用于 OCR 识别场景下的 PDF 扫描件处理
 */
@Slf4j
@Component
public class PdfImageUtils {

    /**
     * 默认的图片 DPI（每英寸点数）
     * DPI 越高，图片越清晰，但文件也越大
     */
    private static final float DEFAULT_DPI = 150f;

    /**
     * 默认的图片格式
     */
    private static final String DEFAULT_IMAGE_FORMAT = "png";

    /**
     * 将 PDF 文件转换为 Base64 编码的图片列表
     * 支持多页 PDF，每一页转换为一张图片
     *
     * @param pdfBytes PDF 文件的字节数组
     * @return Base64 编码的图片列表（不包含 data:image/png;base64,前缀）
     * @throws IOException PDF 读取或图片转换失败时抛出异常
     */
    public List<String> convertPdfToBase64Images(byte[] pdfBytes) throws IOException {
        return convertPdfToBase64Images(pdfBytes, DEFAULT_DPI, DEFAULT_IMAGE_FORMAT);
    }

    /**
     * 将 PDF 文件转换为 Base64 编码的图片列表（自定义 DPI 和图片格式）
     *
     * @param pdfBytes PDF 文件的字节数组
     * @param dpi 渲染 DPI（推荐 150-300）
     * @param imageFormat 图片格式（png/jpg 等）
     * @return Base64 编码的图片列表（不包含 data:image/png;base64,前缀）
     * @throws IOException PDF 读取或图片转换失败时抛出异常
     */
    public List<String> convertPdfToBase64Images(byte[] pdfBytes, float dpi, String imageFormat) throws IOException {
        log.info("开始将 PDF 转换为 Base64 图片，DPI: {}, 格式：{}", dpi, imageFormat);
        
        List<String> base64Images = new ArrayList<>();
        
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            int pageCount = document.getNumberOfPages();
            log.debug("PDF 文档共 {} 页", pageCount);
            
            if (pageCount == 0) {
                log.warn("PDF 文档为空，没有页面");
                return base64Images;
            }
            
            PDFRenderer renderer = new PDFRenderer(document);
            
            for (int i = 0; i < pageCount; i++) {
                log.debug("正在渲染第 {}/{} 页", i + 1, pageCount);
                
                // 渲染 PDF 页面为 BufferedImage
                BufferedImage image = renderer.renderImageWithDPI(i, dpi);
                
                if (image == null) {
                    log.warn("第 {} 页渲染失败，跳过", i + 1);
                    continue;
                }
                
                // 转换为 Base64 编码
                String base64Image = imageToBase64(image, imageFormat);
                base64Images.add(base64Image);
                
                log.debug("第 {} 页转换成功，Base64 长度：{} 字符", i + 1, base64Image.length());
            }
        }
        
        log.info("PDF 转换完成，共 {} 张图片", base64Images.size());
        return base64Images;
    }

    /**
     * 将 BufferedImage 转换为 Base64 编码
     *
     * @param image BufferedImage 对象
     * @param format 图片格式（png/jpg 等）
     * @return Base64 编码字符串（不包含 data:image/png;base64,前缀）
     * @throws IOException 图片写入失败时抛出异常
     */
    public String imageToBase64(BufferedImage image, String format) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // 写入图片到输出流
            boolean success = ImageIO.write(image, format, baos);
            
            if (!success) {
                log.error("图片写入失败，格式：{}", format);
                throw new IOException("图片写入失败，不支持的格式：" + format);
            }
            
            // 转换为 Base64
            byte[] bytes = baos.toByteArray();
            String base64 = Base64.getEncoder().encodeToString(bytes);
            
            log.debug("图片转换成功，原始大小：{} bytes, Base64 大小：{} bytes", 
                     bytes.length, base64.length());
            
            return base64;
        }
    }

    /**
     * 将 PDF 文件转换为单张 Base64 图片（仅第一页）
     * 适用于只需要处理首页的场景
     *
     * @param pdfBytes PDF 文件的字节数组
     * @return Base64 编码的第一页图片
     * @throws IOException PDF 读取或图片转换失败时抛出异常
     */
    public String convertFirstPageToBase64(byte[] pdfBytes) throws IOException {
        return convertFirstPageToBase64(pdfBytes, DEFAULT_DPI, DEFAULT_IMAGE_FORMAT);
    }

    /**
     * 将 PDF 文件转换为单张 Base64 图片（仅第一页，自定义 DPI）
     *
     * @param pdfBytes PDF 文件的字节数组
     * @param dpi 渲染 DPI
     * @param imageFormat 图片格式
     * @return Base64 编码的第一页图片
     * @throws IOException PDF 读取或图片转换失败时抛出异常
     */
    public String convertFirstPageToBase64(byte[] pdfBytes, float dpi, String imageFormat) throws IOException {
        log.info("仅转换 PDF 第一页为 Base64 图片，DPI: {}", dpi);
        
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            int pageCount = document.getNumberOfPages();
            
            if (pageCount == 0) {
                log.warn("PDF 文档为空");
                return null;
            }
            
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImageWithDPI(0, dpi);
            
            if (image == null) {
                log.error("PDF 第一页渲染失败");
                throw new IOException("PDF 第一页渲染失败");
            }
            
            return imageToBase64(image, imageFormat);
        }
    }

    /**
     * 构建完整的 Data URI 格式的 Base64 图片
     * 用于直接嵌入 HTML 或发送给某些 API
     *
     * @param base64Image 纯 Base64 编码（不含前缀）
     * @param mimeType MIME 类型（如 image/png, image/jpeg）
     * @return Data URI 格式的图片字符串
     */
    public String buildDataUri(String base64Image, String mimeType) {
        if (base64Image == null || base64Image.isEmpty()) {
            return null;
        }
        
        String dataUri = "data:" + mimeType + ";base64," + base64Image;
        log.debug("构建 Data URI，MIME 类型：{}, 总长度：{}", mimeType, dataUri.length());
        
        return dataUri;
    }
}
