package cn.ling.domain.vo;

import lombok.Data;

/**
 * 敏感词视图对象
 * 用于向前端返回敏感词数据的展示对象
 * 包含敏感词的基本信息，用于列表展示和详情查看
 */
@Data
public class SensitiveWordsVO {
    /**
     * 敏感词主键ID
     * 唯一标识符，用于前端操作时的数据标识
     */
    private Long id;

    /**
     * 敏感词内容
     * 具体的敏感词汇内容，用于前端展示
     */
    private String word;

    /**
     * 敏感词状态
     * 1-启用状态（该敏感词正在生效）
     * 0-禁用状态（该敏感词已停用）
     */
    private Integer status;

}