package cn.ling.crawler.config;

import cn.ling.crawler.service.CrawlerConfigService;
import cn.ling.crawler.service.WebsiteCrawlerService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
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
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private volatile String effectiveCron = null;
    private volatile LocalDateTime nextExecutionTime = null;

    /**
     * 动态定时检查（每5秒检查一次）
     */
    @Scheduled(fixedDelay = 5000L)
    public synchronized void scheduledCrawl() {
        CrawlerConfigService.CrawlerConfig config = crawlerConfigService.getConfig();
        if (!isScheduleEnabled(config)) {
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

    public synchronized void refreshScheduleState() {
        CrawlerConfigService.CrawlerConfig config = crawlerConfigService.getConfig();
        if (!isScheduleEnabled(config)) {
            resetScheduleState();
            return;
        }

        String cron = normalizeCronExpression(config.getScheduleCron());
        try {
            CronExpression cronExpression = CronExpression.parse(cron);
            effectiveCron = cron;
            nextExecutionTime = cronExpression.next(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("刷新爬虫定时状态失败，cron无效: {}", cron);
            resetScheduleState();
        }
    }

    public synchronized String previewNextExecution(String cron) {
        try {
            CronExpression cronExpression = CronExpression.parse(normalizeCronExpression(cron));
            LocalDateTime next = cronExpression.next(LocalDateTime.now());
            return formatDateTime(next);
        } catch (Exception e) {
            return null;
        }
    }

    public synchronized String getEffectiveCron() {
        return effectiveCron;
    }

    public synchronized String getNextExecutionTimeFormatted() {
        return formatDateTime(nextExecutionTime);
    }

    public synchronized boolean isScheduleActive() {
        return StringUtils.hasText(effectiveCron) && nextExecutionTime != null;
    }

    private boolean isScheduleEnabled(CrawlerConfigService.CrawlerConfig config) {
        return Boolean.TRUE.equals(config.getEnabled())
                && CrawlerConfigService.RUN_MODE_SCHEDULE.equals(crawlerConfigService.normalizeRunMode(config.getRunMode()));
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(DATE_TIME_FORMATTER);
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
