package cn.ling.service;

/**
 * 流式语音识别服务接口
 * 支持长连接模式：开始录音时建立连接，发送音频流，停止录音时关闭连接
 */
public interface SttService {

    /**
     * 开始语音识别会话
     * 建立与模型的WebSocket连接，发送配置消息
     *
     * @param sessionId 会话ID，用于标识本次识别会话
     * @return 会话标识符，用于后续发送音频和停止识别
     */
    String startRecognitionSession(String sessionId);

    /**
     * 发送音频数据
     * 向已建立的识别会话发送音频分片
     *
     * @param sessionToken 会话令牌（由startRecognitionSession返回）
     * @param audioData 音频数据（PCM格式）
     * @return 当前累积的识别结果
     */
    String sendAudioChunk(String sessionToken, byte[] audioData);

    /**
     * 停止语音识别会话
     * 发送结束消息，等待最终识别结果，关闭连接
     *
     * @param sessionToken 会话令牌
     * @return 完整的识别结果
     */
    String stopRecognitionSession(String sessionToken);

    /**
     * 强制停止会话（异常情况下使用）
     * 立即关闭连接，不等待最终结果
     *
     * @param sessionToken 会话令牌
     */
    void forceStopSession(String sessionToken);

    /**
     * 检查会话是否存在
     *
     * @param sessionToken 会话令牌
     * @return 会话是否存在
     */
    boolean isSessionActive(String sessionToken);
}