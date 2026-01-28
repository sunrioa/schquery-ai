package cn.ling.domain.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ASR 模型配置表：asr_model
 */
@TableName(value = "asr_model")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsrModel {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String modelName;
    private String providerName;
    private String modelDescribe;
    private Integer modelShow;
    private String endpoint;
    private String apiKey;
    private String authHeaderName;
    private String authHeaderValue;
    private String subprotocol;
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
    private Integer priority;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
