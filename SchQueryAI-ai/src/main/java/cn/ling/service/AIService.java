package cn.ling.service;

import cn.ling.Result;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI服务接口
 * 提供语音识别和AI对话相关的功能服务
 * 整合了语音转文字和智能对话能力
 */
public interface AIService {

    /**
     * 开始流式语音识别会话
     * 初始化语音识别连接，准备接收音频数据进行实时识别
     *
     * @param sessionId 会话唯一标识符，用于区分不同的识别会话
     * @return Result包含会话令牌，用于后续音频发送和会话控制
     */
    Result<String> startStreamingRecognition(String sessionId);

    /**
     * 发送音频流数据进行识别
     * 将音频文件发送到已建立的语音识别会话进行实时转文字
     *
     * @param sessionToken 会话令牌，由startStreamingRecognition返回
     * @param audioFile 包含音频数据的文件，支持常见音频格式
     * @return Result包含当前识别结果或错误信息
     */
    Result<String> sendStreamingAudio(String sessionToken, MultipartFile audioFile);

    /**
     * 停止流式语音识别会话
     * 结束语音识别并返回最终的完整识别结果
     *
     * @param sessionToken 会话令牌
     * @return Result包含完整的识别文本结果
     */
    Result<String> stopStreamingRecognition(String sessionToken);

    /**
     * 强制停止语音识别会话
     * 在异常情况下强制关闭会话，不等待最终结果
     *
     * @param sessionToken 会话令牌
     * @return Result包含操作结果状态
     */
    Result<String> forceStopStreamingRecognition(String sessionToken);
}
