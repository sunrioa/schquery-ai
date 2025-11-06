package cn.ling.service.impl;

import cn.ling.exception.CustomException;
import cn.ling.handler.SttWebSocketHandler;
import cn.ling.service.SttService;
import cn.ling.utils.FFmpegUtils;
import cn.ling.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class SttServiceImpl implements SttService {

    // 日志记录器：类级别的Logger，统一记录业务日志
    private static final Logger log = LoggerFactory.getLogger(SttService.class);

    /**
     * 从配置文件注入：识别模型的WebSocket服务地址（格式如 ws://ip:port/recognition）
     */
    @Value("${parameters.serverIpPort}")
    private String serverIpPort;

    /**
     * 注入音频格式转换工具：负责将上传的音频（如MP3/WAV）转为模型要求的PCM格式（16kHz/16bit/单声道）
     */
    private final FFmpegUtils fFmpegUtils;

    /**
     * 注入JSON工具：负责构建WebSocket交互的配置消息、结束消息（统一消息格式）
     */
    private final JsonUtils jsonUtils;

    // ===================== 常量配置 =====================
    /** 识别结果等待超时时间（秒）：防止模型处理过慢导致线程阻塞 */
    private static final int TIMEOUT_SECONDS = 120;
    /** PCM音频分片大小（字节）：与模型约定的单次发送最大长度，避免网络拥塞 */
    private static final int BUFFER_SIZE = 960;
    /** WebSocket连接超时时间（毫秒）：防止连接模型服务超时导致的资源浪费 */
    private static final int CONNECT_TIMEOUT = 10000;
    /** 识别重试次数：模型临时不可用时自动重试，提高服务可用性 */
    private static final int MAX_RETRY_COUNT = 3;

    public SttServiceImpl(FFmpegUtils fFmpegUtils , JsonUtils jsonUtils) {
        this.fFmpegUtils = fFmpegUtils;
        this.jsonUtils = jsonUtils;
    }


    /**
     * 核心接口：处理音频文件识别，返回完整识别文本
     * 流程：1.文件校验 → 2.音频转PCM → 3.带重试的WebSocket识别 → 4.返回结果
     *
     * @param sttFile 前端上传的音频文件（支持MP3、WAV等常见格式）
     * @return 识别结果（成功返回文本，失败返回错误描述）
     */
    @Override
    public String getSttResult(MultipartFile sttFile) {

        // 1. 校验文件合法性：空文件直接返回错误
        if (sttFile.isEmpty()) {
            log.warn("识别请求失败：上传文件为空，文件名：{}", sttFile.getName());
            throw CustomException.error("上传文件为空");
        }

        // 2. 音频格式转换：将上传文件转为模型要求的PCM（16kHz/16bit/单声道）
        byte[] pcmData;
        try {
            pcmData = fFmpegUtils.convertAudioToPcm(sttFile);
            log.info("音频格式转换成功，PCM数据长度：{}字节", pcmData.length);
        } catch (Exception e) {
            log.error("音频格式转换失败，文件名：{}，错误原因：{}", sttFile.getOriginalFilename(), e.getMessage(), e); // 打印完整堆栈便于排查
            throw CustomException.error("音频格式转换失败：" + e.getMessage());
        }

        // 3. 带重试机制的WebSocket识别：重试3次，失败则返回最终错误
        String wavName = "recording_" + System.currentTimeMillis(); // 生成唯一音频标识（避免模型混淆）
        String finalResult = "错误：未获取到识别结果"; // 默认错误结果

        for (int retry = 0; retry < MAX_RETRY_COUNT; retry++) {
            try {
                log.info("开始第{}次识别尝试（共{}次），音频标识：{}", retry + 1, MAX_RETRY_COUNT, wavName);
                String currentResult = performRecognition(pcmData, wavName);

                // 若当前结果为有效文本（非错误），更新最终结果并退出重试
                if (!currentResult.startsWith("错误：")) {
                    finalResult = currentResult;
                    log.info("第{}次识别尝试成功，音频标识：{}，识别结果长度：{}字符", retry + 1, wavName, finalResult.length());
                    break;
                }

                // 最后一次重试失败：保留错误信息；非最后一次：等待3秒后重试
                if (retry == MAX_RETRY_COUNT - 1) {
                    finalResult = currentResult;
                    log.error("所有{}次识别尝试均失败，音频标识：{}，最终错误：{}", MAX_RETRY_COUNT, wavName, finalResult);
                } else {
                    log.warn("第{}次识别尝试失败，将在3秒后重试，错误：{}", retry + 1, currentResult);
                    Thread.sleep(3000); // 重试间隔：避免短时间内频繁请求模型
                }
            } catch (InterruptedException e) {
                // 线程中断异常：恢复中断状态，避免线程状态混乱
                Thread.currentThread().interrupt();
                log.error("第{}次识别尝试被中断，音频标识：{}", retry + 1, wavName, e);
                if (retry == MAX_RETRY_COUNT - 1) {
                    finalResult = "错误：识别过程被中断 - " + e.getMessage();
                }
            } catch (Exception e) {
                // 其他未知异常：仅在最后一次重试时记录最终错误
                log.error("第{}次识别尝试发生未知异常，音频标识：{}", retry + 1, wavName, e);
                if (retry == MAX_RETRY_COUNT - 1) {
                    finalResult = "错误：识别过程异常 - " + e.getMessage();
                }
            }
        }

        log.info("语音识别请求处理完成，音频标识：{}，最终结果：{}", wavName, finalResult);
        return finalResult;
    }

    /**
     * 单次识别核心逻辑：建立WebSocket连接、发送消息、接收结果
     * 负责与识别模型的单次完整交互，包括连接建立、配置发送、音频分片发送、结果等待
     *
     * @param pcmData 标准PCM格式的音频数据（16kHz/16bit/单声道）
     * @param wavName 音频唯一标识（用于模型区分不同请求）
     * @return 单次识别结果（成功返回文本，失败返回错误描述）
     * @throws Exception 连接、发送、等待过程中的异常（由上层重试机制处理）
     */
    private String performRecognition(byte[] pcmData, String wavName) throws Exception {
        // 初始化WebSocket客户端：标准Spring WebSocket客户端，负责建立连接
        StandardWebSocketClient client = new StandardWebSocketClient();

        // 初始化WebSocket处理器：负责接收模型返回的消息，累加分片结果
        SttWebSocketHandler handler = new SttWebSocketHandler();

        WebSocketSession session = null; // WebSocket会话对象：需在finally中关闭，避免资源泄漏

        try {
            // 1. 建立WebSocket连接：指定模型地址、处理器、超时时间
            log.debug("开始建立WebSocket连接，模型地址：{}，音频标识：{}", serverIpPort, wavName);
            URI uri = new URI(serverIpPort); // 解析模型服务地址
            session = client.execute(handler, new WebSocketHttpHeaders(), uri).get(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS); // 等待连接建立，超时则抛异常

            if (session == null || !session.isOpen()) {
                log.error("WebSocket连接建立失败（会话为空或未打开），音频标识：{}", wavName);
                throw new RuntimeException("WebSocket连接未建立");
            }
            log.info("WebSocket连接建立成功，会话ID：{}，音频标识：{}", session.getId(), wavName);

            // 2. 发送配置消息：告知模型当前请求的音频标识、识别参数（如热词、模型类型）
            log.debug("发送识别配置消息，音频标识：{}", wavName);
            TextMessage configMsg = jsonUtils.buildConfigMessage(wavName);

            try {
                session.sendMessage(configMsg);
            } catch (IOException e) {
                log.error("发送文本消息失败，会话ID：{}，消息内容：{}", session.getId(), configMsg, e);
                throw e; // 抛出异常，由上层处理（如重试）
            }

            log.debug("配置消息发送完成，音频标识：{}，消息内容：{}", wavName, configMsg);

            // 3. 分片发送PCM音频：按BUFFER_SIZE拆分音频，控制发送速度避免模型拥塞
            log.debug("开始分片发送PCM音频，音频标识：{}，总长度：{}字节，分片大小：{}字节", wavName, pcmData.length, BUFFER_SIZE);
            sendAudioChunks(session, pcmData);

            log.info("PCM音频分片发送完成，音频标识：{}，共发送{}个分片", wavName, (pcmData.length + BUFFER_SIZE - 1) / BUFFER_SIZE); // 向上取整计算分片数

            // 4. 发送结束消息：告知模型当前音频已发送完成，触发模型最终结果计算
            log.debug("发送音频结束消息，音频标识：{}", wavName);
            TextMessage endMsg = jsonUtils.buildEndMessage(wavName);

            try {
                session.sendMessage(endMsg);
            } catch (IOException e) {
                log.error("发送文本消息失败，会话ID：{}，消息内容：{}", session.getId(), endMsg, e);
                throw e; // 抛出异常，由上层处理（如重试）
            }

            log.debug("结束消息发送完成，音频标识：{}，消息内容：{}", wavName, endMsg);

            // 5. 等待识别结果：通过CountDownLatch阻塞，超时则返回超时错误
            log.debug("等待模型返回识别结果，音频标识：{}，超时时间：{}秒", wavName, TIMEOUT_SECONDS);
            CountDownLatch latch = handler.getLatch(); // 从处理器获取同步锁
            boolean isTimeout = !latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            String serverResult = handler.getCompleteResult(); // 获取处理器累加的完整结果

            // 处理超时场景
            if (isTimeout) {
                log.error("等待识别结果超时，音频标识：{}，超时时间：{}秒", wavName, TIMEOUT_SECONDS);
                return "错误：等待结果超时（" + TIMEOUT_SECONDS + "秒）";
            }

            // 处理模型返回的错误
            if (serverResult.startsWith("服务器错误：")) {
                log.error("模型返回错误，音频标识：{}，错误内容：{}", wavName, serverResult);
                return "错误：" + serverResult;
            }

            // 处理空结果场景
            if (StringUtils.hasText(serverResult)) {
                log.debug("获取到完整识别结果，音频标识：{}，结果长度：{}字符", wavName, serverResult.length());
                return serverResult;
            } else {
                log.warn("模型返回空结果，音频标识：{}", wavName);
                return "错误：服务器返回空结果";
            }

        } catch (URISyntaxException e) {
            // 地址格式错误：如IP/端口格式非法，属于配置问题，无需重试
            log.error("WebSocket模型地址格式错误，地址：{}，音频标识：{}", serverIpPort, wavName, e);
            return "错误：服务器地址格式错误 - " + e.getMessage();
        } catch (TimeoutException e) {
            // 连接超时：模型服务未响应，可能是网络问题或模型过载
            log.error("WebSocket连接模型超时，地址：{}，超时时间：{}ms，音频标识：{}", serverIpPort, CONNECT_TIMEOUT, wavName, e);
            return "错误：连接服务器超时（" + CONNECT_TIMEOUT + "ms）";
        } finally {
            // 强制关闭WebSocket会话：无论成功/失败，均释放连接资源，避免内存泄漏
            if (session != null && session.isOpen()) {
                try {
                    log.debug("关闭WebSocket会话，会话ID：{}，音频标识：{}", session.getId(), wavName);
                    session.close(CloseStatus.NORMAL.withReason("客户端主动关闭：识别流程结束"));
                    log.info("WebSocket会话关闭成功，会话ID：{}，音频标识：{}", session.getId(), wavName);
                } catch (IOException e) {
                    // 关闭异常不影响结果返回，仅记录警告（避免掩盖核心错误）
                    log.warn("关闭WebSocket会话时发生异常，会话ID：{}，音频标识：{}", session.getId(), wavName, e);
                }
            }
        }
    }

    /**
     * 分片发送PCM音频数据
     * 按BUFFER_SIZE拆分音频，每次发送一个分片后休眠30ms，匹配模型处理速度，避免网络拥塞
     *
     * @param session WebSocket会话对象（已建立连接）
     * @param pcmData 完整的PCM音频数据
     * @throws IOException 发送过程中出现IO异常（如连接断开）
     */
    private void sendAudioChunks(WebSocketSession session, byte[] pcmData) throws IOException {
        int totalBytes = pcmData.length; // 音频总字节数
        int sentBytes = 0; // 已发送字节数

        while (sentBytes < totalBytes) {
            // 计算当前分片大小：最后一个分片可能小于BUFFER_SIZE
            int chunkSize = Math.min(BUFFER_SIZE, totalBytes - sentBytes);
            byte[] chunk = new byte[chunkSize];
            // 复制当前分片数据（从已发送位置开始，复制chunkSize字节）
            System.arraycopy(pcmData, sentBytes, chunk, 0, chunkSize);

            // 发送二进制消息（PCM是二进制数据，需用BinaryMessage）
            session.sendMessage(new BinaryMessage(chunk));
            sentBytes += chunkSize;

            // 日志：每发送10个分片记录一次进度（避免日志过多）
            if (sentBytes % (BUFFER_SIZE * 10) == 0 || sentBytes == totalBytes) {
                log.debug("音频分片发送进度：{}%（已发送：{}字节 / 总：{}字节），会话ID：{}", (int) ((double) sentBytes / totalBytes * 100), sentBytes, totalBytes, session.getId());
            }

            // 控制发送速度：休眠30ms，匹配模型的处理能力（避免模型来不及处理导致丢包）
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 恢复中断状态
                log.error("音频分片发送被中断，已发送：{}字节 / 总：{}字节，会话ID：{}", sentBytes, totalBytes, session.getId(), e);
                throw new IOException("音频发送被中断", e); // 转为IO异常，由上层处理
            }
        }
    }
}
