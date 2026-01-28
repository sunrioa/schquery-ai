package cn.ling.utils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ASR 运行时配置（用于构建 WebSocket 消息与连接参数）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsrConfig {
    private String endpoint;
    private String apiKey;
    private String authHeaderName;
    private String authHeaderValue;
    private String subprotocol;
    private String model;
    private String format;
    private Integer sampleRate;
    private Boolean enableIntermediateResult;
    private Boolean enablePunctuation;
    private Boolean enableInverseTextNormalization;
    private String hotWords;
    private Boolean useHeaderPayload;
    private Integer chunkBytes;
    private Integer chunkIntervalMs;
    private Integer connectTimeoutMs;
}
