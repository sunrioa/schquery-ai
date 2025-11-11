package cn.ling.service;

import cn.ling.Result;
import org.springframework.web.multipart.MultipartFile;

public interface AIService {
    // 流式语音识别接口
    Result<String> startStreamingRecognition(String sessionId);

    Result<String> sendStreamingAudio(String sessionToken, MultipartFile audioFile);

    Result<String> stopStreamingRecognition(String sessionToken);

    Result<String> forceStopStreamingRecognition(String sessionToken);
}
