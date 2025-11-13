package cn.ling.domain.dto;

import lombok.Data;

/**
 * 分词词库数据传输对象
 * 用于前端与后端之间传输分词词库相关数据
 * 包含分词词汇的基本信息和分页查询参数
 */
@Data
public class SegmentationWordsDTO {
    /**
     * 分词词汇主键ID
     * 用于更新或删除操作时标识具体的分词词汇记录
     */
    private Long id;

    /**
     * 分词词汇内容
     * 用于辅助文本分词处理的词汇
     */
    private String word;

    /**
     * 分词词汇状态
     * 1-启用状态（参与分词处理）
     * 0-禁用状态（不参与分词处理）
     */
    private Integer status;

    /**
     * 当前页码，默认为第1页
     */
    private Long current = 1L;

    /**
     * 每页显示记录数，默认为10条
     */
    private Long size = 10L;
}
