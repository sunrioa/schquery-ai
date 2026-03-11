package cn.ling.service.impl;

import cn.ling.exception.CustomException;
import cn.ling.service.AliyunVLOcrService;
import cn.ling.service.OcrService;
import com.dtflys.forest.exceptions.ForestRuntimeException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * OCR 服务实现类
 * 提供 OCR（光学字符识别）功能，支持 PDF 等文档的文本提取
 * 当前使用阿里云通义千问 VL-OCR 模型进行远程识别
 */
@Slf4j
@Service
public class OcrServiceImpl implements OcrService {

    /**
     * 阿里云 VL-OCR 服务
     * 用于调用通义千问多模态模型进行 OCR 识别
     */
    @Resource
    AliyunVLOcrService aliyunVLOcrService;

    /**
     * 执行 OCR 识别处理
     * 对上传的 PDF 文件进行光学字符识别，提取其中的文本内容
     * 当前使用阿里云通义千问 VL-OCR 模型
     *
     * @param pdf_File 待识别的 PDF 文件
     * @return 识别出的文本内容
     * @throws CustomException 当 OCR 服务不可用或识别失败时抛出业务异常
     */
    @Override
    public String doOcr(MultipartFile pdf_File) {
        log.info("开始 OCR 识别处理（阿里云 VL 模式），文件名：{}, 文件大小：{}字节",
                pdf_File.getOriginalFilename(), pdf_File.getSize());

        try {
            // 使用阿里云通义千问 VL-OCR 模型进行识别
            // 流程：PDF → JavaCV 截图 → Base64 编码 → 阿里云 VL 模型 → 文本
            String extractedText = aliyunVLOcrService.recognizePdf(pdf_File);
            
            log.info("OCR 识别完成（阿里云 VL），文件名：{}, 提取文本长度：{}字符",
                    pdf_File.getOriginalFilename(), extractedText != null ? extractedText.length() : 0);
            
            return extractedText != null ? extractedText : "";

        } catch (IllegalStateException e) {
            // 模型配置缺失
            log.error("OCR 模型配置缺失，文件名：{}, 错误信息：{}",
                    pdf_File.getOriginalFilename(), e.getMessage(), e);
            throw CustomException.error("OCR 模型未配置：请在模型管理中添加并启用 image 分类的 OCR 模型");
        } catch (Exception e) {
            // 捕获其他未知异常
            log.error("OCR 处理过程中发生异常，文件名：{}, 错误信息：{}",
                    pdf_File.getOriginalFilename(), e.getMessage(), e);
            throw CustomException.error("OCR 识别失败：" + e.getMessage());
        }
    }
}
