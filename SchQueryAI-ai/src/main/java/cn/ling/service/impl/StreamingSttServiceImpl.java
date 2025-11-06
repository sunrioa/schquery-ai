package cn.ling.service.impl;

import cn.ling.exception.CustomException;
import cn.ling.handler.SttWebSocketHandler;
import cn.ling.service.StreamingSttService;
import cn.ling.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.UUID;

/**
 * 流式语音识别服务实现
 * 支持长连接模式，管理多个并发的识别会话
 */
@Service
public class StreamingSttServiceImpl implements StreamingSttService {

    private static final Logger log = LoggerFactory.getLogger(StreamingSttService.class);

    /**
     * 模型服务地址
     */
    @Value("${parameters.serverIpPort}")
    private String serverIpPort;

    /**
     * 工具类依赖
     */
    private final JsonUtils jsonUtils;

    /**
     * 会话管理：存储活跃的识别会话
     * Key: sessionToken (UUID)
     * Value: RecognitionSession
     */
    private final ConcurrentHashMap<String, RecognitionSession> activeSessions = new ConcurrentHashMap<>();

    // ===================== 常量配置 =====================
    /** WebSocket连接超时时间（毫秒） */
    private static final int CONNECT_TIMEOUT = 10000;
    /** PCM音频分片大小（字节） */
    private static final int BUFFER_SIZE = 960;
    /** 会话过期时间（分钟） */
    private static final int SESSION_EXPIRE_MINUTES = 30;

    public StreamingSttServiceImpl(JsonUtils jsonUtils) {
        this.jsonUtils = jsonUtils;
    }

    /**
     * 识别会话数据结构
     */
    private static class RecognitionSession {
        private final String sessionToken;
        private final String recognitionId;
        private final WebSocketSession webSocketSession;
        private final SttWebSocketHandler handler;
        private final long createTime;

        public RecognitionSession(String sessionToken, String recognitionId,
                                WebSocketSession webSocketSession, SttWebSocketHandler handler) {
            this.sessionToken = sessionToken;
            this.recognitionId = recognitionId;
            this.webSocketSession = webSocketSession;
            this.handler = handler;
            this.createTime = System.currentTimeMillis();
        }

        // Getters
        public String getSessionToken() { return sessionToken; }
        public String getRecognitionId() { return recognitionId; }
        public WebSocketSession getWebSocketSession() { return webSocketSession; }
        public SttWebSocketHandler getHandler() { return handler; }
        public long getCreateTime() { return createTime; }
    }

    @Override
    public String startRecognitionSession(String sessionId) {
        log.info("开始语音识别会话，前端会话ID：{}", sessionId);

        try {
            // 1. 生成唯一会话令牌
            String sessionToken = UUID.randomUUID().toString();
            String recognitionId = "streaming_" + sessionId + "_" + System.currentTimeMillis();

            // 2. 建立WebSocket连接
            StandardWebSocketClient client = new StandardWebSocketClient();
            SttWebSocketHandler handler = new SttWebSocketHandler();

            URI uri = new URI(serverIpPort);
            WebSocketSession wsSession = client.execute(handler, new WebSocketHttpHeaders(), uri)
                    .get(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS);

            if (wsSession == null || !wsSession.isOpen()) {
                log.error("WebSocket连接建立失败，会话令牌：{}", sessionToken);
                throw CustomException.error("语音识别服务连接失败");
            }

            log.info("WebSocket连接建立成功，会话令牌：{}，WebSocket会话ID：{}", sessionToken, wsSession.getId());

            // 3. 发送配置消息
            TextMessage configMsg = jsonUtils.buildConfigMessage(recognitionId);
            wsSession.sendMessage(configMsg);
            log.debug("配置消息发送完成，会话令牌：{}", sessionToken);

            // 4. 创建并存储会话
            RecognitionSession session = new RecognitionSession(sessionToken, recognitionId, wsSession, handler);
            activeSessions.put(sessionToken, session);

            log.info("语音识别会话创建成功，会话令牌：{}，识别ID：{}", sessionToken, recognitionId);
            return sessionToken;

        } catch (URISyntaxException e) {
            log.error("WebSocket模型地址格式错误，地址：{}，前端会话ID：{}", serverIpPort, sessionId, e);
            throw CustomException.error("语音识别服务地址格式错误");
        } catch (TimeoutException e) {
            log.error("WebSocket连接模型超时，地址：{}，前端会话ID：{}", serverIpPort, sessionId, e);
            throw CustomException.error("语音识别服务连接超时");
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
            WebSocketSession wsSession = session.getWebSocketSession();
            if (!wsSession.isOpen()) {
                log.warn("WebSocket连接已关闭，会话令牌：{}", sessionToken);
                activeSessions.remove(sessionToken);
                return "";
            }

            // 分片发送音频数据
            sendAudioChunks(wsSession, audioData);

            // 获取当前累积的识别结果
            String currentResult = session.getHandler().getCompleteResult();
            log.debug("音频数据发送完成，会话令牌：{}，当前结果长度：{}字符", sessionToken,
                    currentResult != null ? currentResult.length() : 0);

            return currentResult != null ? currentResult : "";

        } catch (Exception e) {
            log.error("发送音频数据失败，会话令牌：{}", sessionToken, e);
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
            WebSocketSession wsSession = session.getWebSocketSession();
            SttWebSocketHandler handler = session.getHandler();

            if (wsSession.isOpen()) {
                // 发送结束消息
                TextMessage endMsg = jsonUtils.buildEndMessage(session.getRecognitionId());
                wsSession.sendMessage(endMsg);
                log.debug("结束消息发送完成，会话令牌：{}", sessionToken);

                // 等待最终识别结果
                boolean isTimeout = !handler.getLatch().await(10, TimeUnit.SECONDS);
                String finalResult = handler.getCompleteResult();

                if (isTimeout) {
                    log.warn("等待最终识别结果超时，会话令牌：{}", sessionToken);
                    finalResult = finalResult != null ? finalResult : "识别超时";
                }

                log.info("语音识别完成，会话令牌：{}，结果长度：{}字符", sessionToken,
                        finalResult != null ? finalResult.length() : 0);

                // 关闭连接
                closeSession(session);
                return finalResult != null ? finalResult : "";

            } else {
                log.warn("WebSocket连接已关闭，无法发送结束消息，会话令牌：{}", sessionToken);
                closeSession(session);
                return "连接已中断";
            }

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
    private void sendAudioChunks(WebSocketSession session, byte[] pcmData) throws IOException {
        int totalBytes = pcmData.length;
        int sentBytes = 0;

        while (sentBytes < totalBytes) {
            int chunkSize = Math.min(BUFFER_SIZE, totalBytes - sentBytes);
            byte[] chunk = new byte[chunkSize];
            System.arraycopy(pcmData, sentBytes, chunk, 0, chunkSize);

            session.sendMessage(new BinaryMessage(chunk));
            sentBytes += chunkSize;

            // 控制发送速度
            try {
                Thread.sleep(10); // 减少延迟，提高实时性
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("音频发送被中断", e);
            }
        }
    }

    /**
     * 关闭会话并清理资源
     */
    private void closeSession(RecognitionSession session) {
        try {
            if (session.getWebSocketSession().isOpen()) {
                session.getWebSocketSession().close(CloseStatus.NORMAL.withReason("识别会话结束"));
            }
        } catch (Exception e) {
            log.warn("关闭WebSocket连接时发生异常，会话令牌：{}", session.getSessionToken(), e);
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
}