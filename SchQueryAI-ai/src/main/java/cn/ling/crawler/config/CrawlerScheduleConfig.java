package cn.ling.crawler.config;

import cn.ling.crawler.service.WebsiteCrawlerService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 爬虫定时任务配置
 */
@Slf4j
@Component
public class CrawlerScheduleConfig {

    @Resource
    private WebsiteCrawlerService crawlerService;

    /**
     * 定时触发爬虫
     * 每周一凌晨 2 点执行
     */
    @Scheduled(cron = "0 0 2 ? * MON")
    public void scheduledCrawl() {
        if (crawlerService.getStatus().isRunning()) {
            log.info("爬虫正在运行中，跳过本次定时任务");
            return;
        }

        log.info("定时触发爬虫...");
        try {
            crawlerService.manualCrawl();
        } catch (Exception e) {
            log.error("定时爬虫执行失败", e);
        }
    }
}
