package cn.ling.crawler.config;

import cn.ling.crawler.service.CrawlerConfigService;
import cn.ling.crawler.service.WebsiteCrawlerService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 爬虫定时任务配置
 */
@Slf4j
@Component
public class CrawlerScheduleConfig {

    @Resource
    private WebsiteCrawlerService crawlerService;

    @Resource
    private CrawlerConfigService crawlerConfigService;

    private static final String DEFAULT_CRON = "0 0 2 ? * MON";

    private volatile String effectiveCron = null;
    private volatile LocalDateTime nextExecutionTime = null;

    /**
     * 动态定时检查（每5秒检查一次）
     */
    @Scheduled(fixedDelay = 5000L)
    public synchronized void scheduledCrawl() {
        CrawlerConfigService.CrawlerConfig config = crawlerConfigService.getConfig();
        if (!Boolean.TRUE.equals(config.getScheduleEnabled())) {
            resetScheduleState();
            return;
        }

        String cron = normalizeCronExpression(config.getScheduleCron());
        CronExpression cronExpression;
        try {
            cronExpression = CronExpression.parse(cron);
        } catch (Exception e) {
            log.warn("定时爬虫cron无效，已跳过: {}", cron);
            resetScheduleState();
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (!Objects.equals(cron, effectiveCron) || nextExecutionTime == null) {
            effectiveCron = cron;
            nextExecutionTime = cronExpression.next(now);
            log.info("爬虫定时任务已更新: cron={}, next={}", effectiveCron, nextExecutionTime);
            return;
        }

        if (nextExecutionTime != null && now.isBefore(nextExecutionTime)) {
            return;
        }

        if (crawlerService.getStatus().isRunning()) {
            log.info("爬虫正在运行中，跳过本次定时任务");
        } else {
            log.info("定时触发爬虫...");
            try {
                boolean started = crawlerService.manualCrawl();
                if (!started) {
                    log.info("爬虫未启动（可能未启用或正在运行）");
                }
            } catch (Exception e) {
                log.error("定时爬虫执行失败", e);
            }
        }

        nextExecutionTime = cronExpression.next(now);
    }

    private String normalizeCronExpression(String cron) {
        if (!StringUtils.hasText(cron)) {
            return DEFAULT_CRON;
        }
        String normalized = cron.trim().replaceAll("\\s+", " ");
        String[] parts = normalized.split(" ");
        if (parts.length == 5) {
            return "0 " + normalized;
        }
        return normalized;
    }

    private void resetScheduleState() {
        effectiveCron = null;
        nextExecutionTime = null;
    }
}
