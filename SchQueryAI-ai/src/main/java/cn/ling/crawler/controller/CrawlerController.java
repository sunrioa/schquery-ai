package cn.ling.crawler.controller;

import cn.ling.Result;
import cn.ling.crawler.service.WebsiteCrawlerService;
import cn.ling.crawler.service.CrawlerConfigService;
import cn.ling.crawler.vo.CrawlerStatusVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 爬虫管理接口
 */
@Slf4j
@RestController
@RequestMapping("/ai/crawler")
public class CrawlerController {

    @Resource
    private WebsiteCrawlerService crawlerService;

    @Resource
    private CrawlerConfigService configService;

    /**
     * 手动触发爬虫
     */
    @PostMapping("/start")
    public Result<String> startCrawl() {
        if (crawlerService.getStatus().isRunning()) {
            return Result.error("爬虫正在运行中，请勿重复触发");
        }

        try {
            crawlerService.manualCrawl();
            return Result.success("爬虫已启动，请查看日志");
        } catch (Exception e) {
            log.error("启动爬虫失败", e);
            return Result.error("启动失败：" + e.getMessage());
        }
    }

    /**
     * 停止爬虫
     */
    @PostMapping("/stop")
    public Result<String> stopCrawl() {
        try {
            crawlerService.stop();
            return Result.success("爬虫已停止");
        } catch (Exception e) {
            log.error("停止爬虫失败", e);
            return Result.error("停止失败：" + e.getMessage());
        }
    }

    /**
     * 查看爬虫状态
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus() {
        CrawlerStatusVO status = crawlerService.getStatus();

        Map<String, Object> data = new HashMap<>();
        data.put("running", status.isRunning());
        data.put("visitedCount", status.getVisitedCount());
        data.put("pendingCount", status.getPendingCount());
        data.put("config", configService.getConfig());

        return Result.success(data);
    }

    /**
     * 获取爬虫配置
     */
    @GetMapping("/config")
    public Result<Object> getConfig() {
        return Result.success(configService.getConfig());
    }

    /**
     * 更新爬虫配置
     */
    @PostMapping("/config/update")
    public Result<String> updateConfig(@RequestParam("key") String key, @RequestParam("value") String value) {
        try {
            configService.updateConfig(key, value);
            return Result.success("配置已更新");
        } catch (Exception e) {
            log.error("更新配置失败", e);
            return Result.error("更新失败：" + e.getMessage());
        }
    }

    /**
     * 生成Cron表达式
     */
    @PostMapping("/cron/generate")
    public Result<String> generateCron(@RequestBody Map<String, Object> params) {
        try {
            String type = (String) params.get("type");
            String cron = generateCronExpression(type, params);
            return Result.success(cron);
        } catch (Exception e) {
            log.error("生成Cron表达式失败", e);
            return Result.error("生成失败：" + e.getMessage());
        }
    }

    /**
     * 根据类型生成Cron表达式
     */
    private String generateCronExpression(String type, Map<String, Object> params) {
        switch (type) {
            case "interval":
                return generateIntervalCron(params);
            case "daily":
                return generateDailyCron(params);
            case "weekly":
                return generateWeeklyCron(params);
            case "monthly":
                return generateMonthlyCron(params);
            default:
                return (String) params.getOrDefault("cron", "0 0 2 ? * MON");
        }
    }

    private String generateIntervalCron(Map<String, Object> params) {
        Integer value = (Integer) params.getOrDefault("value", 1);
        String unit = (String) params.getOrDefault("unit", "hour");
        switch (unit) {
            case "minute":
                return "*/" + value + " * * *";
            case "hour":
                return "0 */" + value + " * * ?";
            case "day":
                return "0 0 */" + value + " * * ?";
            default:
                return "0 0 2 ? * MON";
        }
    }

    private String generateDailyCron(Map<String, Object> params) {
        String time = (String) params.getOrDefault("time", "02:00");
        String[] parts = time.split(":");
        return String.format("%s %s * * ?", parts[1], parts[0]);
    }

    private String generateWeeklyCron(Map<String, Object> params) {
        String time = (String) params.getOrDefault("time", "02:00");
        @SuppressWarnings("unchecked")
        Object daysObj = params.getOrDefault("days", new HashSet());
        Set<Integer> days = Set.of();
        if (daysObj instanceof Set) {
            days = (Set<Integer>) daysObj;
        } else if (daysObj instanceof List) {
            days = new HashSet<>((List<Integer>) daysObj);
        }
        String[] parts = time.split(":");
        return String.format("%s %s %s * ?", parts[1], days.stream().map(String::valueOf).sorted().map(String::valueOf).collect(Collectors.joining(",")));
    }

    private String generateMonthlyCron(Map<String, Object> params) {
        String time = (String) params.getOrDefault("time", "02:00");
        Integer day = (Integer) params.getOrDefault("day", 1);
        String[] parts = time.split(":");
        return String.format("%s %s %s * ?", parts[1], day);
    }
}
