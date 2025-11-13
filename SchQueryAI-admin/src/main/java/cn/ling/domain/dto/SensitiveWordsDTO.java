package cn.ling.domain.dto;

import lombok.Data;

/**
 * 敏感词数据传输对象
 * 用于前端与后端之间传输敏感词相关数据
 * 包含敏感词的基本信息和分页查询参数
 */
@Data
public class SensitiveWordsDTO {
    /**
     * 敏感词主键ID
     * 用于更新或删除操作时标识具体的敏感词记录
     */
    private Long id;

    /**
     * 敏感词内容
     * 需要被系统过滤的敏感词汇
     */
    private String word;

    /**
     * 敏感词状态
     * 1-启用状态（参与敏感词过滤检测）
     * 0-禁用状态（不参与敏感词过滤检测）
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