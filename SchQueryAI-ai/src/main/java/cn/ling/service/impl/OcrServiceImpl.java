package cn.ling.service.impl;

import cn.ling.domain.ocr.OcrHealthResp;
import cn.ling.domain.ocr.OcrResp;
import cn.ling.exception.CustomException;
import cn.ling.rpc.OcrRpc;
import cn.ling.service.OcrService;
import com.dtflys.forest.exceptions.ForestRuntimeException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * OCR服务实现类
 * 提供OCR（光学字符识别）功能，支持PDF等文档的文本提取
 * 通过远程调用OCR服务实现文本识别功能
 */
@Slf4j // 启用SLF4J日志功能
@Service
public class OcrServiceImpl implements OcrService {

    /**
     * OCR远程调用客户端
     * 用于与OCR服务端进行通信，发送健康检查请求和OCR识别请求
     */
    @Resource
    OcrRpc ocrRpc;

    /**
     * 执行OCR识别处理
     * 对上传的PDF文件进行光学字符识别，提取其中的文本内容
     * 在处理前会进行OCR服务健康检查，确保服务可用性
     *
     * @param pdf_File 待识别的PDF文件
     * @return 识别出的文本内容
     * @throws CustomException 当OCR服务不可用或识别失败时抛出业务异常
     */
    @Override
    public String doOcr(MultipartFile pdf_File) {
        log.info("开始OCR识别处理，文件名: {}, 文件大小: {}字节",
                pdf_File.getOriginalFilename(), pdf_File.getSize());

        try {
            // 1. OCR服务健康状态检查：调用服务健康接口，确保服务可正常提供识别能力
            log.debug("开始检查OCR服务健康状态");
            OcrHealthResp ocrHealth = ocrRpc.getOcrStatus();

            // 若健康状态码不为"200"（默认成功状态码），说明服务不可用
            if (!"200".equals(ocrHealth.getCode())){
                log.warn("OCR服务健康检查失败，状态码: {}, 错误信息: {}", ocrHealth.getCode(), ocrHealth.getMsg());
                throw CustomException.error("OCR服务暂时不可用: " + ocrHealth.getMsg());
            }
            log.debug("OCR服务健康检查通过");

            // 2. 调用OCR远程接口，获取识别结果
            log.debug("开始调用OCR识别接口");
            OcrResp result = ocrRpc.getOcrResult(pdf_File);

            // 3. 识别结果校验：确保结果不为空且包含有效数据
            if (result == null || result.getData() == null) {
                log.warn("OCR识别结果为空，无法获取有效内容");
                throw CustomException.error("OCR识别成功，但未获取到有效内容");
            }

            // 4. 识别成功，返回提取的文本数据
            String extractedText = result.getData();
            log.info("OCR识别完成，文件名: {}, 提取文本长度: {}字符",
                    pdf_File.getOriginalFilename(), extractedText.length());
            return extractedText;

        } catch (ForestRuntimeException e) {
            // 捕获Forest框架远程调用异常（如服务连接超时、接口响应异常等）
            log.error("OCR服务远程调用异常，文件名: {}, 错误信息: {}",
                    pdf_File.getOriginalFilename(), e.getMessage(), e);
            throw CustomException.error("OCR服务调用异常: " + e.getMessage());
        } catch (Exception e) {
            // 捕获其他未知异常（如运行时异常、IO异常等），避免服务崩溃
            log.error("OCR处理过程中发生未知异常，文件名: {}, 错误信息: {}",
                    pdf_File.getOriginalFilename(), e.getMessage(), e);
            throw CustomException.error("OCR处理失败: 发生未知错误，请稍后重试");
        }
    }
}
