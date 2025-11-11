package cn.ling.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;

/**
 * 分词词库实体类
 * 对应数据库表 segmentation_words，用于存储分词相关的词汇信息
 */
@TableName(value = "segmentation_words")
@Data
@Builder
public class SegmentationWords {
    /**
     * 主键ID，自增生成
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 分词词内容
     * 用于辅助文本分词处理，提升敏感词检测的准确性
     */
    private String word;

    /**
     * 状态标识
     * 1-启用（该分词词参与分词处理），0-禁用（暂不参与分词处理）
     */
    private Integer status;
}