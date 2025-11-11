package cn.ling.domain.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;

/**
 * 敏感词实体类
 * 对应数据库表 sensitive_words，用于存储系统中的敏感词信息及状态
 */
@TableName(value = "sensitive_words")
@Data
@Builder
public class SensitiveWords {
    /**
     * 主键ID，自增生成
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 敏感词内容
     * 如"垃圾"、"违规"等需要被过滤的词汇
     */
    private String word;

    /**
     * 状态标识
     * 1-启用（该敏感词参与过滤检测），0-禁用（暂不参与过滤检测）
     */
    private Integer status;
}