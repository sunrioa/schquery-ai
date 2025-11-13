package cn.ling.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * OCR（光学字符识别）服务接口
 * 提供文档文字识别功能，支持PDF等格式的文件识别
 * 通过调用Python OCR服务实现文档内容的提取
 */
public interface OcrService {

    /**
     * 执行OCR文字识别
     * 对上传的PDF文件进行文字识别，提取其中的文本内容
     *
     * @param pdfFile 需要进行OCR识别的PDF文件
     * @return 识别后的文本内容，如果识别失败则返回错误信息
     */
    String doOcr(MultipartFile pdfFile);

}
