package cn.ling.crawler.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 爬虫抓取草稿（前端可编辑后再保存）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlerDraftResultVO {
    private String id;
    private String url;
    private String title;
    private String content;
    private Integer contentLength;
    private LocalDateTime fetchTime;
    private LocalDateTime updatedTime;
    private boolean edited;
    private boolean saved;
    private LocalDateTime savedTime;
    private Long knowledgeId;
}
