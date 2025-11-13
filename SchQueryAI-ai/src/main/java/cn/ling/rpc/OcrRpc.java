package cn.ling.rpc;

import cn.ling.domain.ocr.OcrHealthResp;
import cn.ling.domain.ocr.OcrResp;
import com.dtflys.forest.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * OCR服务远程调用接口
 * 使用Forest HTTP客户端框架调用Python OCR服务
 * 提供PDF文件OCR识别和健康检查功能
 * Forest是一个轻量级的HTTP客户端框架，支持声明式HTTP调用
 */
@ForestClient  // 标识该接口为Forest客户端，Forest框架会自动生成实现类处理HTTP请求
@BaseRequest(baseURL = "127.0.0.1:5000")  // 设置OCR服务的基础URL地址
public interface OcrRpc {
    /**
     * 调用Python OCR服务的文档识别接口
     * 发送PDF文件到OCR服务进行文字识别，获取识别后的文本内容
     * 接口特点：
     * - 使用POST方法进行文件上传
     * - 支持大文件处理，设置较长的读取超时时间
     * - 使用multipart/form-data格式上传文件
     *
     * @param pdf_file 需要进行OCR识别的PDF文件，以表单文件字段形式提交
     * @return OcrResp OCR识别响应对象，包含识别状态码、消息和识别结果数据
     */
    @Post(
            url = "/ocr",  // OCR识别接口路径
            readTimeout = 60 * 1000,  // 读取超时时间：60秒（适应大文件处理需求）
            connectTimeout = 3 * 1000,  // 连接超时时间：3秒
            contentType = "multipart/form-data"  // 文件上传内容类型
    )
    OcrResp getOcrResult(
            @DataFile("pdf_file") MultipartFile pdf_file  // PDF文件参数，字段名为"pdf_file"
    );

    /**
     * 调用Python OCR服务的健康检查接口
     * 检查OCR服务的运行状态和可用性
     * 用途：
     * - 监控OCR服务是否正常运行
     * - 在调用OCR识别前检查服务可用性
     * - 用于系统健康检查和监控告警
     *
     * @return OcrHealthResp 健康检查响应对象，包含服务状态信息
     */
    @Get(
            url = "/health",  // 健康检查接口路径
            readTimeout = 3 * 1000,  // 读取超时时间：3秒（健康检查响应通常很快）
            connectTimeout = 3 * 1000  // 连接超时时间：3秒
    )
    OcrHealthResp getOcrStatus();
}
