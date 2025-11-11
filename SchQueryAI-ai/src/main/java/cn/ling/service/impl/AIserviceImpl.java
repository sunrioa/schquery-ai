package cn.ling.service.impl;

import cn.ling.Result;
import cn.ling.service.AIService;
//import cn.ling.service.SttService;
import cn.ling.service.SttService;
import cn.ling.utils.FFmpegUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AIserviceImpl implements AIService {

    @Autowired
    private SttService sttService;

    @Autowired
    private FFmpegUtils fFmpegUtils;

    @Override
    public Result<String> startStreamingRecognition(String sessionId) {
        try {
            String sessionToken = sttService.startRecognitionSession(sessionId);
            return Result.success(sessionToken);
        } catch (Exception e) {
            return Result.error("启动语音识别失败：" + e.getMessage());
        }
    }

    @Override
    public Result<String> sendStreamingAudio(String sessionToken, MultipartFile audioFile) {
        try {
            if (audioFile.isEmpty()) {
                return Result.error("音频文件为空");
            }

            // 转换音频为PCM格式
            byte[] pcmData = fFmpegUtils.convertAudioToPcm(audioFile);

            // 发送音频数据
            String currentResult = sttService.sendAudioChunk(sessionToken, pcmData);
            return Result.success(currentResult);

        } catch (Exception e) {
            return Result.error("发送音频数据失败：" + e.getMessage());
        }
    }

    @Override
    public Result<String> stopStreamingRecognition(String sessionToken) {
        try {
            String finalResult = sttService.stopRecognitionSession(sessionToken);
            return Result.success(finalResult);
        } catch (Exception e) {
            return Result.error("停止语音识别失败：" + e.getMessage());
        }
    }

    @Override
    public Result<String> forceStopStreamingRecognition(String sessionToken) {
        try {
            sttService.forceStopSession(sessionToken);
            return Result.success("会话已强制停止");
        } catch (Exception e) {
            return Result.error("强制停止失败：" + e.getMessage());
        }
    }
}
