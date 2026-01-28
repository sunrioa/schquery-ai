package cn.ling.handler;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.*;
import java.util.concurrent.CountDownLatch;

/**
 * 语音识别WebSocket处理器
 * 实现Spring WebSocketHandler接口，用于处理与阿里云语音识别服务的WebSocket通信
 * 支持实时语音识别结果接收和错误处理，使用同步机制等待识别完成
 */
public class SttWebSocketHandler implements WebSocketHandler {
    // 日志记录器
    private static final Logger logger = LoggerFactory.getLogger(SttWebSocketHandler.class);

    /**
     * -- GETTER --
     *  获取同步锁实例
     *  供外部线程等待WebSocket消息处理完成
     *
     */
    // 同步锁：用于等待服务器返回最终结果，初始计数为1
    @Getter
    private final CountDownLatch latch = new CountDownLatch(1);

    // 结果累加器：用于存储所有分片结果，使用线程安全的StringBuilder
    private final StringBuilder completeResult = new StringBuilder();

    // 错误标记：用于存储错误信息，优先处理服务器返回的错误
    private String errorMsg = null;

    /**
     * 获取完整的处理结果
     * 错误信息优先于正常结果返回
     *
     * @return 处理结果字符串，包含错误信息或累加的识别文本
     */
    public String getCompleteResult() {
        if (errorMsg != null) {
            logger.debug("返回错误结果: {}", errorMsg);
            return "服务器错误：" + errorMsg;
        }
        String result = completeResult.toString().trim();
        logger.debug("返回正常结果，长度: {}字符", result.length());
        return result;
    }

    /**
     * 当WebSocket连接建立后调用
     *
     * @param session WebSocket会话对象
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("WebSocket连接已建立，会话ID: {}", session.getId());
        logger.debug("连接远程地址: {}", session.getRemoteAddress());
    }

    /**
     * 处理接收到的WebSocket消息
     * 解析JSON消息，处理错误信息，累加识别结果，判断是否为最终结果
     *
     * @param session WebSocket会话对象
     * @param message 接收到的WebSocket消息
     */
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        logger.info("收到WebSocket消息，会话ID: {}", session.getId());

        try {
            // 只处理文本消息
            if (message instanceof TextMessage) {
                // 获取消息内容
                String payload = ((TextMessage) message).getPayload();
                logger.debug("消息内容: {}", payload);

                // 使用FastJSON解析JSON字符串
                JSONObject json = JSONObject.parseObject(payload);

                // 1. 优先处理服务器错误（一旦出错，直接标记）
                String error = extractError(json);
                if (StringUtils.hasText(error)) {
                    errorMsg = error;
                    logger.error("服务器返回错误: {}", errorMsg);
                    latch.countDown(); // 触发等待结束
                    return;
                }

                // 2. 累加识别分片（每段结果都拼接到completeResult）
                String segment = extractText(json);
                if (StringUtils.hasText(segment)) { // 只拼接非空片段
                    appendSegment(segment);
                    logger.debug("累加识别片段，当前累计长度: {}字符，片段内容: {}", completeResult.length(), segment);
                } else {
                    logger.debug("收到空的识别片段，跳过拼接");
                }

                // 3. 收到"最终结果"标识时，触发等待结束（确保所有分片已接收）
                if (isFinalResult(json)) {
                    logger.info("收到最终结果标识，结束等待，累计结果长度: {}字符", completeResult.length());
                    latch.countDown();
                }
            } else {
                logger.warn("收到非文本类型消息，类型: {}", message.getClass().getSimpleName());
            }
        } catch (Exception e) {
            // 解析消息出错，标记错误
            errorMsg = "消息解析失败：" + e.getMessage();
            logger.error("消息处理异常", e);
            if (latch.getCount() > 0) {
                latch.countDown();
            }
        }
    }

    /**
     * 处理WebSocket传输错误
     *
     * @param session WebSocket会话对象
     * @param exception 发生的异常
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        errorMsg = "WebSocket传输错误：" + exception.getMessage();
        logger.error("WebSocket传输错误，会话ID: {}", session.getId(), exception);
        // 确保只递减一次
        if (latch.getCount() > 0) {
            latch.countDown();
        }
    }

    /**
     * 当WebSocket连接关闭后调用
     *
     * @param session WebSocket会话对象
     * @param status 关闭状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        logger.info("WebSocket连接已关闭，会话ID: {}，关闭状态: {}({})",
                session.getId(), status.getCode(), status.getReason());

        // 连接意外关闭且未获取结果时，标记错误
        if (latch.getCount() > 0 && errorMsg == null && completeResult.isEmpty()) {
            errorMsg = "连接意外关闭：" + status.getReason();
            logger.warn("连接意外关闭且未获取有效结果，会话ID: {}", session.getId());
            latch.countDown();
        }
    }

    /**
     * 是否支持部分消息
     *
     * @return false - 不支持部分消息（与服务器约定）
     */
    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private void appendSegment(String segment) {
        String trimmed = segment.trim();
        if (!StringUtils.hasText(trimmed)) {
            return;
        }

        String current = completeResult.toString();
        if (StringUtils.hasText(current) && trimmed.startsWith(current)) {
            completeResult.setLength(0);
        }
        completeResult.append(trimmed);
    }

    private String extractError(JSONObject json) {
        String error = json.getString("error");
        if (StringUtils.hasText(error)) {
            return error;
        }

        Integer code = json.getInteger("code");
        if (code != null && code != 0 && code != 200) {
            String message = json.getString("message");
            return StringUtils.hasText(message) ? message : "服务端错误，错误码: " + code;
        }

        JSONObject header = json.getJSONObject("header");
        if (header != null) {
            Integer status = header.getInteger("status");
            if (status != null && status != 0 && status != 20000000) {
                String message = header.getString("message");
                return StringUtils.hasText(message) ? message : "服务端错误，状态码: " + status;
            }
        }

        return null;
    }

    private String extractText(JSONObject json) {
        String text = firstNonEmpty(
                json.getString("text"),
                json.getString("result"),
                json.getString("sentence"),
                json.getString("transcript")
        );
        if (StringUtils.hasText(text)) {
            return text.trim();
        }

        JSONObject payload = json.getJSONObject("payload");
        if (payload != null) {
            text = firstNonEmpty(
                    payload.getString("text"),
                    payload.getString("result"),
                    payload.getString("sentence"),
                    payload.getString("transcript")
            );
            if (StringUtils.hasText(text)) {
                return text.trim();
            }
            text = extractFromResults(payload.getJSONArray("results"));
            if (StringUtils.hasText(text)) {
                return text.trim();
            }
        }

        JSONObject output = json.getJSONObject("output");
        if (output != null) {
            text = firstNonEmpty(
                    output.getString("text"),
                    output.getString("result"),
                    output.getString("sentence"),
                    output.getString("transcript")
            );
            if (StringUtils.hasText(text)) {
                return text.trim();
            }
            text = extractFromResults(output.getJSONArray("results"));
            if (StringUtils.hasText(text)) {
                return text.trim();
            }
        }

        return null;
    }

    private String extractFromResults(JSONArray results) {
        if (results == null || results.isEmpty()) {
            return null;
        }
        JSONObject first = results.getJSONObject(0);
        if (first == null) {
            return null;
        }
        return firstNonEmpty(
                first.getString("text"),
                first.getString("result"),
                first.getString("sentence"),
                first.getString("transcript")
        );
    }

    private boolean isFinalResult(JSONObject json) {
        if (json.getBooleanValue("is_final") || json.getBooleanValue("final")) {
            return true;
        }

        JSONObject payload = json.getJSONObject("payload");
        if (payload != null && (payload.getBooleanValue("is_final") || payload.getBooleanValue("final"))) {
            return true;
        }

        JSONObject header = json.getJSONObject("header");
        if (header != null) {
            String name = firstNonEmpty(header.getString("name"), header.getString("event"));
            if (StringUtils.hasText(name)) {
                String normalized = name.toLowerCase();
                if (normalized.contains("completed")
                        || normalized.contains("sentenceend")
                        || normalized.contains("finish")
                        || normalized.contains("done")) {
                    return true;
                }
            }
        }

        return false;
    }

    private String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
