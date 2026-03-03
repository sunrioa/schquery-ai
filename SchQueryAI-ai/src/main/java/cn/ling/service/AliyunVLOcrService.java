package cn.ling.service;

import cn.ling.domain.pojo.ChatModel;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 阿里云通义千问 VL-OCR 服务接口
 * 提供基于多模态大模型的 OCR 识别能力
 * 支持 PDF、图片等格式的文本提取
 */
public interface AliyunVLOcrService {

    /**
     * 对 PDF 文件进行 OCR 识别
     * 自动将 PDF 转换为图片，调用阿里云 VL 模型识别
     *
     * @param file PDF 文件
     * @return 识别出的文本内容
     * @throws Exception 识别失败时抛出异常
     */
    String recognizePdf(MultipartFile file) throws Exception;

    /**
     * 对 Base64 图片进行 OCR 识别
     * 直接调用阿里云 VL 模型识别单张图片
     *
     * @param base64Image Base64 编码的图片（不含前缀）
     * @param mimeType MIME 类型（如 image/png, image/jpeg）
     * @return 识别出的文本内容
     * @throws Exception 识别失败时抛出异常
     */
    String recognizeBase64Image(String base64Image, String mimeType) throws Exception;

    /**
     * 对多张 Base64 图片进行批量 OCR 识别
     * 按顺序识别所有图片并拼接结果
     *
     * @param base64Images Base64 图片列表
     * @param mimeType MIME 类型
     * @return 识别出的文本内容（所有页面拼接）
     * @throws Exception 识别失败时抛出异常
     */
    String recognizeMultipleImages(List<String> base64Images, String mimeType) throws Exception;

    /**
     * 使用指定的 ChatModel 配置进行 OCR 识别
     * 允许自定义模型配置
     *
     * @param chatModel 模型配置
     * @param base64Image Base64 图片
     * @param mimeType MIME 类型
     * @return 识别出的文本内容
     * @throws Exception 识别失败时抛出异常
     */
    String recognizeWithModel(ChatModel chatModel, String base64Image, String mimeType) throws Exception;
}
