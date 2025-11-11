package cn.ling.domain.dto;

import lombok.Data;

@Data
public class SegmentationWordsDTO {
    private Long id;

    private String word;

    private Integer status;

    // 分页参数
    /**
     * 当前页码，默认为1
     */
    private Long current = 1L;

    /**
     * 每页大小，默认为10
     */
    private Long size = 10L;
}
