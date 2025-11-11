package cn.ling.domain.ocr;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OCR健康信息识别响应实体类
 * 用于封装OCR识别健康相关信息后的返回结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OcrHealthResp {
    /**
     * 响应状态码
     * 通常用于表示请求处理的结果状态，如成功、失败等
     */
    String code;

    /**
     * 响应消息
     * 用于描述请求处理的结果信息，如成功提示或错误原因
     */
    String msg;

    /**
     * 响应数据
     * 存储OCR识别后的健康相关数据内容，具体格式根据业务场景而定
     */
    String data;
}