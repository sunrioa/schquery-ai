package cn.ling.utils;

import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.TextMessage;

/**
 * JSON消息构建工具类
 * 负责创建与音频处理相关的配置消息和结束消息
 */
@Component
public class JsonUtils {
    // 日志记录器
    private static final Logger logger = LoggerFactory.getLogger(JsonUtils.class);

    /**
     * 模型模式（从配置文件读取，默认值为"offline"）
     * 可能的取值："online"表示在线模式，"offline"表示离线模式
     */
    @Value("${parameters.model:offline}")
    private String model;

    /**
     * 热词（从配置文件读取，默认空字符串）
     * 用于提升特定词汇的识别准确率
     */
    @Value("${parameters.hotWords:}")
    private String hotWords;

    /**
     * 音频采样率，固定为16000Hz
     * 与音频处理流程中使用的采样率保持一致
     */
    private static final int AUDIO_SAMPLE_RATE = 16000;

    /**
     * 使用FastJSON构建音频处理的配置消息
     * 包含处理模式、音频信息、采样率等关键参数
     *
     * @param wavName 音频文件名，用于标识当前处理的音频
     * @return 构建好的配置消息TextMessage对象
     */
    public TextMessage buildConfigMessage(String wavName) {
        logger.info("开始构建配置消息，音频文件名: {}", wavName);

        // 使用FastJSON的JSONObject创建配置消息
        JSONObject config = new JSONObject();

        // 设置处理模式（在线/离线）
        config.put("mode", model);
        logger.debug("配置消息添加模式: {}", model);

        // 设置音频文件名
        config.put("wav_name", wavName);

        // 设置音频格式为PCM
        config.put("wav_format", "pcm");
        logger.debug("配置消息音频格式: pcm");

        // 标记为正在说话状态
        config.put("is_speaking", true);

        // 设置音频采样率
        config.put("audio_fs", AUDIO_SAMPLE_RATE);
        logger.debug("配置消息采样率: {}", AUDIO_SAMPLE_RATE);

        // 启用数字转换（将语音中的数字转换为阿拉伯数字）
        config.put("itn", true);

        // 设置分片大小参数
        int[] chunkSizes = new int[]{5, 10, 5};
        config.put("chunk_size", chunkSizes);
        logger.debug("配置消息分片大小: {}", chunkSizes);

        // 当热词不为空时添加热词配置
        if (StringUtils.hasText(hotWords)) {
            config.put("hotwords", hotWords);
            logger.debug("配置消息添加热词: {}", hotWords);
        } else {
            logger.debug("未配置热词，不添加hotwords字段");
        }

        logger.info("配置消息构建完成，内容: {}", config);
        return new TextMessage(config.toString());
    }

    /**
     * 使用FastJSON构建音频处理的结束消息
     * 用于通知服务端当前音频处理已完成
     *
     * @param wavName 音频文件名，用于标识已完成处理的音频
     * @return 构建好的结束消息TextMessage对象
     */
    public TextMessage buildEndMessage(String wavName) {
        logger.info("开始构建结束消息，音频文件名: {}", wavName);

        // 使用FastJSON的JSONObject创建结束消息
        JSONObject endMsg = new JSONObject();

        // 设置音频文件名
        endMsg.put("wav_name", wavName);

        // 标记为非说话状态
        endMsg.put("is_speaking", false);

        // 标记为结束标识
        endMsg.put("end", true);

        // 设置处理模式（与配置消息保持一致）
        endMsg.put("mode", model);
        logger.debug("结束消息模式: {}", model);

        logger.info("结束消息构建完成，内容: {}", endMsg);
        return new TextMessage(endMsg.toString());
    }
}

