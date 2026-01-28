package cn.ling.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.TextMessage;

import java.util.HashMap;
import java.util.Map;

/**
 * JSON消息构建工具类
 * 负责创建与音频处理相关的配置消息和结束消息
 * 使用FastJSON构建WebSocket通信所需的JSON格式消息，支持语音识别服务的配置和控制
 */
@Slf4j
@Component
public class JsonUtils {

    /**
     * 识别模型（从配置文件读取）
     */
    @Value("${parameters.asr.model:fun-asr-realtime-2025-11-07}")
    private String model;

    /**
     * 音频格式
     */
    @Value("${parameters.asr.format:pcm}")
    private String format;

    /**
     * 音频采样率
     */
    @Value("${parameters.asr.sampleRate:16000}")
    private int sampleRate;

    /**
     * 是否启用中间结果
     */
    @Value("${parameters.asr.enableIntermediateResult:true}")
    private boolean enableIntermediateResult;

    /**
     * 是否启用标点
     */
    @Value("${parameters.asr.enablePunctuation:true}")
    private boolean enablePunctuation;

    /**
     * 是否启用数字转换
     */
    @Value("${parameters.asr.enableInverseTextNormalization:true}")
    private boolean enableInverseTextNormalization;

    /**
     * 热词（从配置文件读取，默认空字符串）
     * 用于提升特定词汇的识别准确率
     */
    @Value("${parameters.asr.hotWords:}")
    private String hotWords;

    /**
     * 是否使用header/payload结构
     */
    @Value("${parameters.asr.useHeaderPayload:true}")
    private boolean useHeaderPayload;

    /**
     * 使用FastJSON构建音频处理的配置消息
     * 包含处理模式、音频信息、采样率等关键参数
     *
     * @param wavName 音频文件名，用于标识当前处理的音频
     * @return 构建好的配置消息TextMessage对象
     */
    public TextMessage buildConfigMessage(String wavName) {
        return buildConfigMessage(wavName, null);
    }

    public TextMessage buildConfigMessage(String wavName, AsrConfig config) {
        log.info("开始构建配置消息，音频文件名: {}", wavName);

        String resolvedModel = pickString(config == null ? null : config.getModel(), model);
        String resolvedFormat = pickString(config == null ? null : config.getFormat(), format);
        Integer resolvedSampleRate = pickInteger(config == null ? null : config.getSampleRate(), sampleRate);
        boolean resolvedIntermediate = pickBoolean(config == null ? null : config.getEnableIntermediateResult(), enableIntermediateResult);
        boolean resolvedPunctuation = pickBoolean(config == null ? null : config.getEnablePunctuation(), enablePunctuation);
        boolean resolvedItn = pickBoolean(
                config == null ? null : config.getEnableInverseTextNormalization(),
                enableInverseTextNormalization
        );
        String resolvedHotWords = pickString(config == null ? null : config.getHotWords(), hotWords);
        boolean resolvedUseHeaderPayload = pickBoolean(config == null ? null : config.getUseHeaderPayload(), useHeaderPayload);

        // 使用FastJSON的JSONObject创建配置消息
        JSONObject configJson = new JSONObject();
        JSONObject payload = new JSONObject();

        if (StringUtils.hasText(resolvedModel)) {
            payload.put("model", resolvedModel);
        }
        payload.put("format", resolvedFormat);
        payload.put("sample_rate", resolvedSampleRate);
        payload.put("enable_intermediate_result", resolvedIntermediate);
        payload.put("enable_punctuation", resolvedPunctuation);
        payload.put("enable_inverse_text_normalization", resolvedItn);

        if (StringUtils.hasText(resolvedHotWords)) {
            payload.put("hotwords", parseHotWords(resolvedHotWords));
            log.debug("配置消息添加热词: {}", resolvedHotWords);
        } else {
            log.debug("未配置热词，不添加hotwords字段");
        }

        if (resolvedUseHeaderPayload) {
            JSONObject header = new JSONObject();
            header.put("action", "start");
            header.put("task_id", wavName);
            configJson.put("header", header);
            configJson.put("payload", payload);
        } else {
            configJson.put("action", "start");
            configJson.put("task_id", wavName);
            configJson.putAll(payload);
        }

        log.info("配置消息构建完成，内容: {}", configJson);
        return new TextMessage(configJson.toString());
    }

    /**
     * 使用FastJSON构建音频处理的结束消息
     * 用于通知服务端当前音频处理已完成
     *
     * @param wavName 音频文件名，用于标识已完成处理的音频
     * @return 构建好的结束消息TextMessage对象
     */
    public TextMessage buildEndMessage(String wavName) {
        return buildEndMessage(wavName, null);
    }

    public TextMessage buildEndMessage(String wavName, AsrConfig config) {
        log.info("开始构建结束消息，音频文件名: {}", wavName);

        boolean resolvedUseHeaderPayload = pickBoolean(config == null ? null : config.getUseHeaderPayload(), useHeaderPayload);

        // 使用FastJSON的JSONObject创建结束消息
        JSONObject endMsg = new JSONObject();
        if (resolvedUseHeaderPayload) {
            JSONObject header = new JSONObject();
            header.put("action", "stop");
            header.put("task_id", wavName);
            endMsg.put("header", header);
        } else {
            endMsg.put("action", "stop");
            endMsg.put("task_id", wavName);
        }

        log.info("结束消息构建完成，内容: {}", endMsg);
        return new TextMessage(endMsg.toString());
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

    private Object parseHotWords(String hotWords) {
        try {
            return JSON.parse(hotWords);
        } catch (Exception e) {
            return hotWords;
        }
    }

    public static Map<String,Object> strToMap(String str){
        HashMap<String, Object> map = new HashMap<>();
        try {
            // 使用 FastJSON2 解析 JSON 字符串
            JSONObject jsonObject = JSONObject.parseObject(str);
            // 将 JSONObject 转换为 Map
            map = new HashMap<>(jsonObject);
        } catch (Exception e) {
            // 异常处理：可以根据需要记录日志或抛出运行时异常
            throw new RuntimeException("JSON 解析失败: " + e.getMessage());
        }
        return map;
    }
}

