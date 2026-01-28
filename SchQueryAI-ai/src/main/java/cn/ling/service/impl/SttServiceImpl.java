package cn.ling.service.impl;

import cn.ling.domain.pojo.AsrModel;
import cn.ling.exception.CustomException;
import cn.ling.service.AsrModelService;
import cn.ling.service.SttService;
import cn.ling.utils.AsrConfig;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.alibaba.dashscope.utils.Constants;
import com.alibaba.fastjson2.JSON;
import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.PublishProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

/**
 * 流式语音识别服务实现
 * 支持长连接模式，管理多个并发的识别会话
 */
@Slf4j // 启用SLF4J日志功能
@Service
public class SttServiceImpl implements SttService {

    /**
     * 模型服务地址
     */
    @Value("${parameters.asr.endpoint}")
    private String asrEndpoint;

    @Value("${parameters.asr.apiKey:}")
    private String apiKey;

    @Value("${parameters.asr.authHeaderName:Authorization}")
    private String authHeaderName;

    @Value("${parameters.asr.authHeaderValue:}")
    private String authHeaderValue;

    @Value("${parameters.asr.subprotocol:}")
    private String subprotocol;

    @Value("${parameters.asr.connectTimeoutMs:10000}")
    private int connectTimeoutMs;

    @Value("${parameters.asr.model:fun-asr-realtime-2025-11-07}")
    private String defaultModel;

    @Value("${parameters.asr.format:pcm}")
    private String defaultFormat;

    @Value("${parameters.asr.sampleRate:16000}")
    private int defaultSampleRate;

    @Value("${parameters.asr.enableIntermediateResult:true}")
    private boolean defaultEnableIntermediateResult;

    @Value("${parameters.asr.enablePunctuation:true}")
    private boolean defaultEnablePunctuation;

    @Value("${parameters.asr.enableInverseTextNormalization:true}")
    private boolean defaultEnableInverseTextNormalization;

    @Value("${parameters.asr.hotWords:}")
    private String defaultHotWords;

    @Value("${parameters.asr.useHeaderPayload:true}")
    private boolean defaultUseHeaderPayload;

    @Value("${parameters.asr.chunkBytes:960}")
    private int chunkBytes;

    @Value("${parameters.asr.chunkIntervalMs:10}")
    private int chunkIntervalMs;

    @Resource
    private AsrModelService asrModelService;

    /**
     * 会话管理：存储活跃的识别会话
     * Key: sessionToken (UUID)
     * Value: RecognitionSession
     */
    private final ConcurrentHashMap<String, RecognitionSession> activeSessions = new ConcurrentHashMap<>();

    // ===================== 常量配置 =====================
    /** 会话过期时间（分钟） */
    private static final int SESSION_EXPIRE_MINUTES = 30;

    /**
     * 识别会话数据结构
     */
    @Getter
    private static class RecognitionSession {
        // Getters
        private final String sessionToken;
        private final String recognitionId;
        private final Recognition recognizer;
        private final FlowableProcessor<ByteBuffer> audioProcessor;
        private final CountDownLatch completionLatch;
        private final AsrConfig asrConfig;
        private final long createTime;
        @Setter
        private volatile boolean isSending = false; // 发送状态锁
        private final Object sendLock = new Object(); // 同步锁
        private final Object resultLock = new Object();
        private final StringBuilder completeResult = new StringBuilder();
        @Setter
        private volatile String errorMsg;
        @Setter
        private volatile Disposable resultDisposable;

        public RecognitionSession(String sessionToken, String recognitionId,
                                Recognition recognizer,
                                FlowableProcessor<ByteBuffer> audioProcessor,
                                AsrConfig asrConfig) {
            this.sessionToken = sessionToken;
            this.recognitionId = recognitionId;
            this.recognizer = recognizer;
            this.audioProcessor = audioProcessor;
            this.asrConfig = asrConfig;
            this.createTime = System.currentTimeMillis();
            this.completionLatch = new CountDownLatch(1);
        }

        public void appendResult(String segment) {
            if (!StringUtils.hasText(segment)) {
                return;
            }
            String trimmed = segment.trim();
            if (!StringUtils.hasText(trimmed)) {
                return;
            }

            synchronized (resultLock) {
                String current = completeResult.toString();
                if (StringUtils.hasText(current) && trimmed.startsWith(current)) {
                    completeResult.setLength(0);
                }
                completeResult.append(trimmed);
            }
        }

        public String getCompleteResult() {
            if (StringUtils.hasText(errorMsg)) {
                return "服务器错误：" + errorMsg;
            }
            synchronized (resultLock) {
                return completeResult.toString().trim();
            }
        }
    }

    @Override
    public String startRecognitionSession(String sessionId) {
        log.info("开始语音识别会话，前端会话ID：{}", sessionId);

        String endpointForLog = asrEndpoint;
        try {
            AsrConfig asrConfig = resolveAsrConfig();
            String endpoint = asrConfig.getEndpoint();
            endpointForLog = endpoint;
            if (!StringUtils.hasText(endpoint)) {
                throw CustomException.error("语音识别服务地址未配置");
            }

            // 1. 生成唯一会话令牌
            String sessionToken = UUID.randomUUID().toString();
            String recognitionId = "streaming_" + sessionId + "_" + System.currentTimeMillis();

            Constants.baseWebsocketApiUrl = endpoint;
            RecognitionParam param = buildRecognitionParam(asrConfig);

            Recognition recognizer = new Recognition();
            FlowableProcessor<ByteBuffer> audioProcessor = PublishProcessor.<ByteBuffer>create().toSerialized();
            RecognitionSession session = new RecognitionSession(sessionToken, recognitionId, recognizer, audioProcessor, asrConfig);

            try {
                Flowable<RecognitionResult> resultFlowable = recognizer.streamCall(param, audioProcessor);
                Disposable disposable = resultFlowable.subscribe(
                        result -> handleRecognitionResult(session, result),
                        error -> handleRecognitionError(session, error),
                        () -> handleRecognitionComplete(session)
                );
                session.setResultDisposable(disposable);
            } catch (Exception e) {
                closeSession(session);
                throw e;
            }

            activeSessions.put(sessionToken, session);

            log.info("语音识别会话创建成功，会话令牌：{}，识别ID：{}", sessionToken, recognitionId);
            return sessionToken;

        } catch (Exception e) {
            log.error("创建语音识别会话失败，前端会话ID：{}", sessionId, e);
            throw CustomException.error("语音识别会话创建失败：" + e.getMessage());
        }
    }

    @Override
    public String sendAudioChunk(String sessionToken, byte[] audioData) {
        log.debug("接收音频数据，会话令牌：{}，数据长度：{}字节", sessionToken, audioData.length);

        RecognitionSession session = activeSessions.get(sessionToken);
        if (session == null) {
            log.warn("会话不存在或已过期，会话令牌：{}", sessionToken);
            return "";
        }

        try {
            if (session.getAudioProcessor().hasComplete() || session.getAudioProcessor().hasThrowable()) {
                log.warn("音频流已结束或出现异常，会话令牌：{}", sessionToken);
                return session.getCompleteResult();
            }

            // 使用同步锁确保音频数据按顺序发送
            synchronized (session.getSendLock()) {
                // 检查是否有其他线程正在发送
                if (session.isSending()) {
                    log.debug("音频发送繁忙，跳过本次发送，会话令牌：{}", sessionToken);
                    return session.getCompleteResult();
                }

                session.setSending(true);
                try {
                    // 分片发送音频数据
                    sendAudioChunks(session, audioData);
                } finally {
                    session.setSending(false);
                }
            }

            // 获取当前累积的识别结果
            String currentResult = session.getCompleteResult();
            log.debug("音频数据发送完成，会话令牌：{}，当前结果长度：{}字符", sessionToken,
                    currentResult != null ? currentResult.length() : 0);

            return currentResult != null ? currentResult : "";

        } catch (Exception e) {
            log.error("发送音频数据失败，会话令牌：{}", sessionToken, e);
            // 确保发送状态被重置
            session.setSending(false);
            // 不抛出异常，避免中断录音流程
            return "";
        }
    }

    @Override
    public String stopRecognitionSession(String sessionToken) {
        log.info("停止语音识别会话，会话令牌：{}", sessionToken);

        RecognitionSession session = activeSessions.get(sessionToken);
        if (session == null) {
            log.warn("会话不存在或已过期，会话令牌：{}", sessionToken);
            return "会话已过期";
        }

        try {
            // 等待当前音频发送完成
            synchronized (session.getSendLock()) {
                while (session.isSending()) {
                    log.debug("等待音频发送完成，会话令牌：{}", sessionToken);
                    try {
                        Thread.sleep(100); // 等待100ms
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("等待音频发送被中断，会话令牌：{}", sessionToken);
                        break;
                    }
                }

                if (!session.getAudioProcessor().hasComplete()) {
                    session.getAudioProcessor().onComplete();
                }
            }

            boolean isTimeout = !session.getCompletionLatch().await(10, TimeUnit.SECONDS);
            String finalResult = session.getCompleteResult();

            if (isTimeout) {
                log.warn("等待最终识别结果超时，会话令牌：{}", sessionToken);
                finalResult = StringUtils.hasText(finalResult) ? finalResult : "识别超时";
            }

            log.info("语音识别完成，会话令牌：{}，结果长度：{}字符", sessionToken,
                    finalResult != null ? finalResult.length() : 0);

            closeSession(session);
            return finalResult != null ? finalResult : "";

        } catch (Exception e) {
            log.error("停止语音识别会话失败，会话令牌：{}", sessionToken, e);
            closeSession(session);
            return "识别过程异常：" + e.getMessage();
        }
    }

    @Override
    public void forceStopSession(String sessionToken) {
        log.info("强制停止语音识别会话，会话令牌：{}", sessionToken);

        RecognitionSession session = activeSessions.get(sessionToken);
        if (session != null) {
            closeSession(session);
        }
    }

    @Override
    public boolean isSessionActive(String sessionToken) {
        return activeSessions.containsKey(sessionToken);
    }

    /**
     * 分片发送PCM音频数据
     */
    private void sendAudioChunks(RecognitionSession session, byte[] pcmData) throws IOException {
        AsrConfig asrConfig = session.getAsrConfig();
        int resolvedChunkBytes = asrConfig.getChunkBytes() != null ? asrConfig.getChunkBytes() : chunkBytes;
        int resolvedIntervalMs = asrConfig.getChunkIntervalMs() != null ? asrConfig.getChunkIntervalMs() : chunkIntervalMs;
        int totalBytes = pcmData.length;
        int sentBytes = 0;

        while (sentBytes < totalBytes) {
            if (session.getAudioProcessor().hasComplete() || session.getAudioProcessor().hasThrowable()) {
                log.warn("音频流已结束或出现异常，停止发送音频数据，会话令牌：{}", session.getSessionToken());
                break;
            }

            int chunkSize = Math.min(resolvedChunkBytes, totalBytes - sentBytes);
            ByteBuffer chunkBuffer = ByteBuffer.wrap(pcmData, sentBytes, chunkSize);

            try {
                session.getAudioProcessor().onNext(chunkBuffer);
                sentBytes += chunkSize;
            } catch (Exception e) {
                log.error("发送音频分片失败，已发送：{}字节，总分片：{}字节", sentBytes, totalBytes, e);
                throw new IOException("音频发送失败", e);
            }

            // 控制发送速度
            if (resolvedIntervalMs > 0) {
                try {
                    Thread.sleep(resolvedIntervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("音频发送被中断", e);
                }
            }
        }
    }

    /**
     * 关闭会话并清理资源
     */
    private void closeSession(RecognitionSession session) {
        try {
            if (!session.getAudioProcessor().hasComplete() && !session.getAudioProcessor().hasThrowable()) {
                session.getAudioProcessor().onComplete();
            }
        } catch (Exception e) {
            log.warn("关闭音频流时发生异常，会话令牌：{}", session.getSessionToken(), e);
        }

        try {
            Disposable disposable = session.getResultDisposable();
            if (disposable != null && !disposable.isDisposed()) {
                disposable.dispose();
            }
        } catch (Exception e) {
            log.warn("关闭结果订阅时发生异常，会话令牌：{}", session.getSessionToken(), e);
        }

        try {
            session.getRecognizer().getDuplexApi().close(1000, "bye");
        } catch (Exception e) {
            log.warn("关闭DashScope连接时发生异常，会话令牌：{}", session.getSessionToken(), e);
        } finally {
            activeSessions.remove(session.getSessionToken());
            log.debug("会话资源清理完成，会话令牌：{}", session.getSessionToken());
        }
    }

    /**
     * 清理过期会话（可由定时任务调用）
     */
    public void cleanExpiredSessions() {
        long currentTime = System.currentTimeMillis();
        long expireTime = currentTime - (SESSION_EXPIRE_MINUTES * 60 * 1000L);

        activeSessions.entrySet().removeIf(entry -> {
            RecognitionSession session = entry.getValue();
            if (session.getCreateTime() < expireTime) {
                log.info("清理过期会话，会话令牌：{}", session.getSessionToken());
                closeSession(session);
                return true;
            }
            return false;
        });
    }

    private AsrConfig resolveAsrConfig() {
        AsrModel model = null;
        try {
            model = asrModelService.getHighestPriorityEnabled();
        } catch (Exception e) {
            log.warn("读取ASR模型配置失败，将使用application.yml默认参数: {}", e.getMessage());
        }
        if (model != null) {
            log.info("使用ASR模型配置: modelName={}, provider={}", model.getModelName(), model.getProviderName());
        } else {
            log.info("未配置ASR模型，使用application.yml默认参数");
        }

        return AsrConfig.builder()
                .endpoint(pickString(model == null ? null : model.getEndpoint(), asrEndpoint))
                .apiKey(pickString(model == null ? null : model.getApiKey(), apiKey))
                .authHeaderName(pickString(model == null ? null : model.getAuthHeaderName(), authHeaderName))
                .authHeaderValue(pickString(model == null ? null : model.getAuthHeaderValue(), authHeaderValue))
                .subprotocol(pickString(model == null ? null : model.getSubprotocol(), subprotocol))
                .model(pickString(model == null ? null : model.getModelName(), defaultModel))
                .format(pickString(model == null ? null : model.getFormat(), defaultFormat))
                .sampleRate(pickInteger(model == null ? null : model.getSampleRate(), defaultSampleRate))
                .enableIntermediateResult(pickBoolean(
                        model == null ? null : model.getEnableIntermediateResult(),
                        defaultEnableIntermediateResult
                ))
                .enablePunctuation(pickBoolean(model == null ? null : model.getEnablePunctuation(), defaultEnablePunctuation))
                .enableInverseTextNormalization(pickBoolean(
                        model == null ? null : model.getEnableInverseTextNormalization(),
                        defaultEnableInverseTextNormalization
                ))
                .hotWords(pickString(model == null ? null : model.getHotWords(), defaultHotWords))
                .useHeaderPayload(pickBoolean(model == null ? null : model.getUseHeaderPayload(), defaultUseHeaderPayload))
                .chunkBytes(pickInteger(model == null ? null : model.getChunkBytes(), chunkBytes))
                .chunkIntervalMs(pickInteger(model == null ? null : model.getChunkIntervalMs(), chunkIntervalMs))
                .connectTimeoutMs(pickInteger(model == null ? null : model.getConnectTimeoutMs(), connectTimeoutMs))
                .build();
    }

    private String pickString(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private Integer pickInteger(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    private boolean pickBoolean(Boolean value, boolean fallback) {
        return value != null ? value : fallback;
    }

    private RecognitionParam buildRecognitionParam(AsrConfig asrConfig) {
        RecognitionParam.RecognitionParamBuilder<?, ?> builder = RecognitionParam.builder();
        String resolvedModel = pickString(asrConfig.getModel(), defaultModel);
        if (!StringUtils.hasText(resolvedModel)) {
            throw CustomException.error("语音识别模型名称未配置");
        }
        builder.model(resolvedModel);

        String resolvedFormat = pickString(asrConfig.getFormat(), defaultFormat);
        if (StringUtils.hasText(resolvedFormat)) {
            builder.format(resolvedFormat);
        }

        Integer resolvedSampleRate = pickInteger(asrConfig.getSampleRate(), defaultSampleRate);
        if (resolvedSampleRate != null && resolvedSampleRate > 0) {
            builder.sampleRate(resolvedSampleRate);
        }

        String resolvedApiKey = pickString(asrConfig.getApiKey(), apiKey);
        if (!StringUtils.hasText(resolvedApiKey)) {
            resolvedApiKey = normalizeApiKey(pickString(asrConfig.getAuthHeaderValue(), authHeaderValue));
        }
        if (StringUtils.hasText(resolvedApiKey)) {
            builder.apiKey(resolvedApiKey);
        }

        applyOptionalParams(builder, asrConfig);
        return builder.build();
    }

    private String normalizeApiKey(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        String trimmed = rawValue.trim();
        if (trimmed.toLowerCase().startsWith("bearer ")) {
            return trimmed.substring("bearer ".length()).trim();
        }
        return trimmed;
    }

    private void applyOptionalParams(RecognitionParam.RecognitionParamBuilder<?, ?> builder, AsrConfig asrConfig) {
        addParam(builder, "enable_intermediate_result", asrConfig.getEnableIntermediateResult());
        addParam(builder, "enable_punctuation", asrConfig.getEnablePunctuation());
        addParam(builder, "enable_inverse_text_normalization", asrConfig.getEnableInverseTextNormalization());

        Object hotWords = parseHotWords(asrConfig.getHotWords());
        if (hotWords != null) {
            addParam(builder, "hotwords", hotWords);
        }
    }

    private Object parseHotWords(String hotWords) {
        if (!StringUtils.hasText(hotWords)) {
            return null;
        }
        try {
            return JSON.parse(hotWords);
        } catch (Exception e) {
            return hotWords.trim();
        }
    }

    private void addParam(RecognitionParam.RecognitionParamBuilder<?, ?> builder, String key, Object value) {
        if (value == null) {
            return;
        }
        builder.parameter(key, value);
    }

    private void handleRecognitionResult(RecognitionSession session, RecognitionResult result) {
        if (result == null || result.getSentence() == null) {
            return;
        }
        String text = result.getSentence().getText();
        if (StringUtils.hasText(text)) {
            session.appendResult(text);
        }
    }

    private void handleRecognitionError(RecognitionSession session, Throwable error) {
        String message = error != null ? error.getMessage() : "识别异常";
        session.setErrorMsg(message);
        log.error("语音识别流式回调异常，会话令牌：{}", session.getSessionToken(), error);
        if (session.getCompletionLatch().getCount() > 0) {
            session.getCompletionLatch().countDown();
        }
    }

    private void handleRecognitionComplete(RecognitionSession session) {
        if (session.getCompletionLatch().getCount() > 0) {
            session.getCompletionLatch().countDown();
        }
    }
}
