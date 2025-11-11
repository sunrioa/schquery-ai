package cn.ling.rpc;

import cn.ling.domain.ocr.OcrHealthResp;
import cn.ling.domain.ocr.OcrResp;
import com.dtflys.forest.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@ForestClient  // 标识该接口为Forest客户端，Forest框架会自动生成实现类处理HTTP请求
@BaseRequest(baseURL = "127.0.0.1:5000")
public interface OcrRpc {
    /**
     * 调用Python OCR服务的/ocr接口，获取PDF文件的原始解析结果
     * @param pdf_file 需要解析的PDF文件对象，将以multipart/form-data形式上传
     * @return OcrResult 解析结果响应对象，包含解析状态、消息和具体内容
     */
    @Post(
            url = "/ocr",  // 接口请求路径
            readTimeout = 60 * 1000,  // 读取超时时间，60秒（单位：毫秒）
            connectTimeout = 3 * 1000,  // 连接超时时间，3秒（单位：毫秒）
            contentType = "multipart/form-data"  // 请求内容类型，适用于文件上传
    )
    OcrResp getOcrResult(
            @DataFile("pdf_file") MultipartFile pdf_file  // 标记为文件参数，表单字段名为"pdf_file"
    );

    /**
     * 调用Python OCR服务的/health接口，获取服务状态
     * @return OcrResult 结果响应对象，包含状态、消息和具体内容
     */
    @Get(
            url = "/health",  // 接口请求路径
            readTimeout = 3 * 1000,  // 读取超时时间，3秒（单位：毫秒）
            connectTimeout = 3 * 1000  // 连接超时时间，3秒（单位：毫秒）
    )
    OcrHealthResp getOcrStatus();
}
