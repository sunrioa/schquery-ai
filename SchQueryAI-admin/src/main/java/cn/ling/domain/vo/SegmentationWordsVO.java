package cn.ling.domain.vo;

import lombok.Data;

/**
 * 分词词库视图对象
 * 用于向前端返回分词词库数据的展示对象
 * 包含分词词汇的基本信息，用于列表展示和详情查看
 */
@Data
public class SegmentationWordsVO {
    /**
     * 分词词汇主键ID
     * 唯一标识符，用于前端操作时的数据标识
     */
    private Long id;

    /**
     * 分词词汇内容
     * 具体的分词词汇内容，用于前端展示
     */
    private String word;

    /**
     * 分词词汇状态
     * 1-启用状态（该分词词汇正在生效）
     * 0-禁用状态（该分词词汇已停用）
     */
    private Integer status;

}
