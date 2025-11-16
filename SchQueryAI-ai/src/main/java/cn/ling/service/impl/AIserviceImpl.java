package cn.ling.service.impl;

import cn.ling.Result;
import cn.ling.service.AIService;
import cn.ling.service.SttService;
import cn.ling.utils.FFmpegUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI服务实现类
 * 提供语音识别相关的核心功能，包括流式语音识别会话管理、音频数据处理等
 */
@Slf4j // 启用SLF4J日志功能
@Service
public class AIserviceImpl implements AIService {

    @Resource
    private SttService sttService;

    @Resource
    private FFmpegUtils fFmpegUtils;

    @Resource(name = "ocrCorrectChatClient")
    private ChatClient ocrCorrectChatClient;

    @Override
    public String ocrCorrect(String text) {
        return ocrCorrectChatClient.prompt(text).call().content();
    }

    /**
     * 开始流式语音识别会话
     * 初始化一个新的语音识别会话，并返回会话令牌用于后续的音频数据传输
     *
     * @param sessionId 会话ID，用于唯一标识本次语音识别会话
     * @return 包含会话令牌的结果对象
     */
    @Override
    public Result<String> startStreamingRecognition(String sessionId) {
        log.info("开始启动流式语音识别会话，sessionId: {}", sessionId);

        try {
            // 调用STT服务启动识别会话
            String sessionToken = sttService.startRecognitionSession(sessionId);

            log.info("流式语音识别会话启动成功，sessionId: {}, sessionToken: {}", sessionId, sessionToken);
            return Result.success(sessionToken);
        } catch (Exception e) {
            log.error("启动流式语音识别会话失败，sessionId: {}, 错误信息: {}", sessionId, e.getMessage(), e);
            return Result.error("启动语音识别失败：" + e.getMessage());
        }
    }

    /**
     * 发送流式音频数据
     * 将音频文件转换为PCM格式并发送给STT服务进行实时识别
     *
     * @param sessionToken 会话令牌，用于标识识别会话
     * @param audioFile 音频文件，支持多种音频格式
     * @return 包含当前识别结果的结果对象
     */
    @Override
    public Result<String> sendStreamingAudio(String sessionToken, MultipartFile audioFile) {
        log.info("开始发送流式音频数据，sessionToken: {}, 文件名: {}, 文件大小: {}字节",
                sessionToken, audioFile.getOriginalFilename(), audioFile.getSize());

        try {
            // 参数验证：检查音频文件是否为空
            if (audioFile.isEmpty()) {
                log.warn("音频文件为空，sessionToken: {}", sessionToken);
                return Result.error("音频文件为空");
            }

            // 使用FFmpeg工具将音频文件转换为PCM格式
            log.debug("开始转换音频文件为PCM格式，sessionToken: {}", sessionToken);
            byte[] pcmData = fFmpegUtils.convertAudioToPcm(audioFile);
            log.debug("音频文件转换完成，PCM数据大小: {}字节", pcmData.length);

            // 发送PCM数据到STT服务进行识别
            log.debug("发送音频数据到STT服务，sessionToken: {}", sessionToken);
            String currentResult = sttService.sendAudioChunk(sessionToken, pcmData);

            log.info("音频数据发送成功，sessionToken: {}, 识别结果长度: {}", sessionToken,
                    currentResult != null ? currentResult.length() : 0);
            return Result.success(currentResult);

        } catch (Exception e) {
            log.error("发送流式音频数据失败，sessionToken: {}, 文件名: {}, 错误信息: {}",
                    sessionToken, audioFile.getOriginalFilename(), e.getMessage(), e);
            return Result.error("发送音频数据失败：" + e.getMessage());
        }
    }

    /**
     * 停止流式语音识别会话
     * 正常结束识别会话，并获取最终的完整识别结果
     *
     * @param sessionToken 会话令牌，用于标识要停止的识别会话
     * @return 包含最终识别结果的结果对象
     */
    @Override
    public Result<String> stopStreamingRecognition(String sessionToken) {
        log.info("开始停止流式语音识别会话，sessionToken: {}", sessionToken);

        try {
            // 调用STT服务停止识别会话并获取最终结果
            String finalResult = sttService.stopRecognitionSession(sessionToken);

            log.info("流式语音识别会话停止成功，sessionToken: {}, 最终结果长度: {}",
                    sessionToken, finalResult != null ? finalResult.length() : 0);
            return Result.success(finalResult);
        } catch (Exception e) {
            log.error("停止流式语音识别会话失败，sessionToken: {}, 错误信息: {}", sessionToken, e.getMessage(), e);
            return Result.error("停止语音识别失败：" + e.getMessage());
        }
    }

    /**
     * 强制停止流式语音识别会话
     * 立即终止识别会话，不等待最终结果，用于异常情况下的会话清理
     *
     * @param sessionToken 会话令牌，用于标识要强制停止的识别会话
     * @return 包含停止状态信息的结果对象
     */
    @Override
    public Result<String> forceStopStreamingRecognition(String sessionToken) {
        log.info("开始强制停止流式语音识别会话，sessionToken: {}", sessionToken);

        try {
            // 调用STT服务强制停止会话
            sttService.forceStopSession(sessionToken);

            log.info("流式语音识别会话强制停止成功，sessionToken: {}", sessionToken);
            return Result.success("会话已强制停止");
        } catch (Exception e) {
            log.error("强制停止流式语音识别会话失败，sessionToken: {}, 错误信息: {}", sessionToken, e.getMessage(), e);
            return Result.error("强制停止失败：" + e.getMessage());
        }
    }
}
