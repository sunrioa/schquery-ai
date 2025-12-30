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
 * AI模型配置表：chat_model
 */
@TableName(value = "chat_model")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatModel {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String category;
    private String modelName;
    private String providerName;
    private String modelDescribe;
    private Double modelPrice;
    private String modelType;
    private Integer modelShow;
    private String systemPrompt;
    private String apiHost;
    private String apiKey;
    private String apiUrl;
    private Integer priority;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

