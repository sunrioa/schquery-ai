package cn.ling.crawler.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 爬虫状态 VO（供 Controller 返回）
 */
@Data
@AllArgsConstructor
public class CrawlerStatusVO {
    /**
     * 是否运行中
     */
    private boolean running;

    /**
     * 已访问数量
     */
    private int visitedCount;

    /**
     * 待处理数量
     */
    private int pendingCount;
}
