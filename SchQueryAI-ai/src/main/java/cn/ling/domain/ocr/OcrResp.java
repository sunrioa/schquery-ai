package cn.ling.domain.ocr;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OCR识别响应实体类
 * 用于封装OCR（光学字符识别）服务调用后的返回结果
 * 包含响应状态码、消息内容和识别数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OcrResp {

    /**
     * 响应状态码
     * 用于表示OCR服务调用的结果状态
     * 通常是HTTP状态码或自定义业务状态码
     */
    String code;

    /**
     * 响应消息
     * 用于描述OCR服务调用的结果信息
     * 如成功提示、错误原因等详细信息
     */
    String msg;

    /**
     * OCR识别数据
     * 存储OCR识别后的文本内容数据
     * 具体格式根据OCR服务的输出而定，通常为JSON格式的文本
     */
    String data;

}