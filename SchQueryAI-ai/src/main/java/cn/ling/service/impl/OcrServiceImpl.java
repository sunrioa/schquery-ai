package cn.ling.service.impl;

import cn.ling.domain.ocr.OcrHealthResp;
import cn.ling.domain.ocr.OcrResp;
import cn.ling.exception.CustomException;
import cn.ling.rpc.OcrRpc;
import cn.ling.service.OcrService;
import com.dtflys.forest.exceptions.ForestRuntimeException;
import jakarta.annotation.Resource;
import org.springframework.web.multipart.MultipartFile;

public class OcrServiceImpl implements OcrService {

    /**
     * OCR远程调用客户端
     * 用于与OCR服务端进行通信，发送健康检查请求和OCR识别请求
     */
    @Resource
    OcrRpc ocrRpc;

    @Override
    public String doOcr(MultipartFile pdf_File) {
        try {
            // 2. OCR服务健康状态检查：调用服务健康接口，确保服务可正常提供识别能力
            OcrHealthResp ocrHealth = ocrRpc.getOcrStatus();
            // 若健康状态码不为"200"（默认成功状态码），说明服务不可用
            if (!"200".equals(ocrHealth.getCode())){
                throw CustomException.error("OCR服务暂时不可用: " + ocrHealth.getMsg());
            }
            // 调用OCR远程接口，获取识别结果
            OcrResp result = ocrRpc.getOcrResult(pdf_File);

            // 5. 识别结果校验：确保结果不为空且包含有效数据
            if (result == null || result.getData() == null) {
                throw CustomException.error("OCR识别成功，但未获取到有效内容");
            }
            // 识别成功，返回提取的文本数据
            return result.getData();
        }  catch (ForestRuntimeException e) {
            // 捕获Forest框架远程调用异常（如服务连接超时、接口响应异常等）
            throw CustomException.error("OCR服务调用异常: " + e.getMessage());
        } catch (Exception e) {
            // 捕获其他未知异常（如运行时异常、IO异常等），避免服务崩溃
            throw CustomException.error("OCR处理失败: 发生未知错误，请稍后重试");
        }
    }
}
