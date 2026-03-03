package cn.ling.crawler.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 爬虫草稿保存结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlerSaveResultVO {
    private Integer submittedCount;
    private Integer skippedCount;
    private Long knowledgeId;
    private List<String> failedItems;
    private List<CrawlerDraftResultVO> savedItems;
}
