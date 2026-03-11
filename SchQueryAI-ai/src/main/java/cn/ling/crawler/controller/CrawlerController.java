package cn.ling.crawler.controller;

import cn.ling.Result;
import cn.ling.crawler.config.CrawlerScheduleConfig;
import cn.ling.crawler.service.WebsiteCrawlerService;
import cn.ling.crawler.service.CrawlerConfigService;
import cn.ling.crawler.vo.CrawlerDraftResultVO;
import cn.ling.crawler.vo.CrawlerSaveResultVO;
import cn.ling.crawler.vo.CrawlerStatusVO;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

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

    @Resource
    private CrawlerScheduleConfig crawlerScheduleConfig;

    /**
     * 手动触发爬虫
     */
    @PostMapping("/start")
    public Result<String> startCrawl(@RequestBody(required = false) StartCrawlRequest request) {
        try {
            String startUrl = request == null ? null : request.getStartUrl();
            Integer requestIntervalMs = request == null ? null : request.getRequestIntervalMs();
            CrawlerConfigService.CrawlerConfig config = configService.getConfig();
            String runMode = configService.normalizeRunMode(request == null ? null : request.getRunMode());
            if (!StringUtils.hasText(request == null ? null : request.getRunMode())) {
                runMode = configService.normalizeRunMode(config.getRunMode());
            }

            if (!StringUtils.hasText(startUrl)
                    && (!StringUtils.hasText(config.getBaseUrl()) || !StringUtils.hasText(config.getStartUrl()))) {
                return Result.error("请先配置基础URL和起始URL");
            }

            if (CrawlerConfigService.RUN_MODE_SCHEDULE.equals(runMode)) {
                String nextExecutionTime = crawlerScheduleConfig.previewNextExecution(config.getScheduleCron());
                if (!StringUtils.hasText(nextExecutionTime)) {
                    return Result.error("当前 Cron 表达式无效，无法启动自动爬取");
                }
                configService.setCrawlerEnabled(true);
                crawlerScheduleConfig.refreshScheduleState();
                return Result.success("自动爬取已启动，下次执行时间：" + nextExecutionTime);
            }

            configService.setCrawlerEnabled(true);
            boolean started = crawlerService.manualCrawl(startUrl, requestIntervalMs);
            if (!started) {
                configService.setCrawlerEnabled(false);
                return Result.error("爬虫正在运行中或配置不可用");
            }
            crawlerScheduleConfig.refreshScheduleState();
            return Result.success("已开始执行一次完整爬取，任务完成后会自动停止");
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
            configService.setCrawlerEnabled(false);
            crawlerService.stop();
            crawlerScheduleConfig.refreshScheduleState();
            return Result.success("爬虫已停止，自动调度已关闭");
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
        CrawlerConfigService.CrawlerConfig config = configService.getConfig();

        Map<String, Object> data = new HashMap<>();
        data.put("running", status.isRunning());
        data.put("enabled", Boolean.TRUE.equals(config.getEnabled()));
        data.put("visitedCount", status.getVisitedCount());
        data.put("pendingCount", status.getPendingCount());
        data.put("draftCount", crawlerService.getDraftResultCount());
        data.put("scheduleActive", crawlerScheduleConfig.isScheduleActive());
        data.put("effectiveCron", crawlerScheduleConfig.getEffectiveCron());
        data.put("nextExecutionTime", crawlerScheduleConfig.getNextExecutionTimeFormatted());
        data.put("config", config);

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
            crawlerScheduleConfig.refreshScheduleState();
            return Result.success("配置已更新");
        } catch (Exception e) {
            log.error("更新配置失败", e);
            return Result.error("更新失败：" + e.getMessage());
        }
    }

    /**
     * 获取抓取草稿列表
     */
    @GetMapping("/results")
    public Result<List<CrawlerDraftResultVO>> getResults() {
        return Result.success(crawlerService.listDraftResults());
    }

    /**
     * 更新抓取草稿（前端编辑后保存草稿）
     */
    @PostMapping("/results/update")
    public Result<CrawlerDraftResultVO> updateResult(@RequestBody DraftUpdateRequest request) {
        try {
            CrawlerDraftResultVO updated = crawlerService.updateDraftResult(request.getId(), request.getTitle(), request.getContent());
            return Result.success(updated, "草稿已更新");
        } catch (Exception e) {
            return Result.error("更新草稿失败：" + e.getMessage());
        }
    }

    /**
     * 保存选中草稿到知识库（触发向量化）
     */
    @PostMapping("/results/save")
    public Result<CrawlerSaveResultVO> saveResults(@RequestBody DraftSaveRequest request) {
        try {
            List<String> ids = request == null ? Collections.emptyList() : request.getIds();
            Long knowledgeId = request == null ? null : request.getKnowledgeId();
            CrawlerSaveResultVO result = crawlerService.saveDraftResults(ids, knowledgeId);
            return Result.success(result, "已提交保存任务");
        } catch (Exception e) {
            return Result.error("保存失败：" + e.getMessage());
        }
    }

    /**
     * 清空抓取草稿
     */
    @PostMapping("/results/clear")
    public Result<String> clearResults() {
        crawlerService.clearDraftResults();
        return Result.success("草稿已清空");
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
                return normalizeCronExpression((String) params.getOrDefault("cron", "0 0 2 ? * MON"));
        }
    }

    @PostMapping("/cron/next-execution")
    public Result<Map<String, Object>> previewCronNextExecution(@RequestBody Map<String, Object> params) {
        String cron = normalizeCronExpression((String) params.get("cron"));
        String nextExecutionTime = crawlerScheduleConfig.previewNextExecution(cron);

        Map<String, Object> data = new HashMap<>();
        data.put("cron", cron);
        data.put("valid", StringUtils.hasText(nextExecutionTime));
        data.put("nextExecutionTime", nextExecutionTime);
        return Result.success(data);
    }

    private String generateIntervalCron(Map<String, Object> params) {
        Integer value = parsePositiveInt(params.get("value"), 1);
        String unit = (String) params.getOrDefault("unit", "hour");
        switch (unit) {
            case "minute":
                return String.format("0 */%d * * * ?", value);
            case "hour":
                return String.format("0 0 */%d * * ?", value);
            case "day":
                return String.format("0 0 0 */%d * ?", value);
            default:
                return "0 0 2 ? * MON";
        }
    }

    private String generateDailyCron(Map<String, Object> params) {
        String time = (String) params.getOrDefault("time", "02:00");
        int[] hourMinute = parseHourMinute(time);
        return String.format("0 %d %d * * ?", hourMinute[1], hourMinute[0]);
    }

    private String generateWeeklyCron(Map<String, Object> params) {
        String time = (String) params.getOrDefault("time", "02:00");
        int[] hourMinute = parseHourMinute(time);
        Object daysObj = params.getOrDefault("days", Collections.singletonList(1));
        Set<String> days = new LinkedHashSet<>();

        if (daysObj instanceof Collection<?>) {
            for (Object day : (Collection<?>) daysObj) {
                String mapped = mapDayOfWeek(day);
                if (mapped != null) {
                    days.add(mapped);
                }
            }
        } else {
            String mapped = mapDayOfWeek(daysObj);
            if (mapped != null) {
                days.add(mapped);
            }
        }

        if (days.isEmpty()) {
            days.add("MON");
        }

        return String.format("0 %d %d ? * %s", hourMinute[1], hourMinute[0], String.join(",", days));
    }

    private String generateMonthlyCron(Map<String, Object> params) {
        String time = (String) params.getOrDefault("time", "02:00");
        int[] hourMinute = parseHourMinute(time);
        Integer day = parsePositiveInt(params.get("day"), 1);
        if (day > 31) {
            day = 31;
        }
        return String.format("0 %d %d %d * ?", hourMinute[1], hourMinute[0], day);
    }

    private int[] parseHourMinute(String time) {
        if (!StringUtils.hasText(time) || !time.contains(":")) {
            return new int[]{2, 0};
        }
        String[] parts = time.split(":");
        try {
            int hour = Math.max(0, Math.min(23, Integer.parseInt(parts[0])));
            int minute = Math.max(0, Math.min(59, Integer.parseInt(parts[1])));
            return new int[]{hour, minute};
        } catch (Exception e) {
            return new int[]{2, 0};
        }
    }

    private Integer parsePositiveInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value));
            return parsed > 0 ? parsed : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String mapDayOfWeek(Object dayObj) {
        if (dayObj == null) {
            return null;
        }
        String value = String.valueOf(dayObj).trim();
        if (value.isEmpty()) {
            return null;
        }
        switch (value.toUpperCase()) {
            case "0":
            case "7":
            case "SUN":
                return "SUN";
            case "1":
            case "MON":
                return "MON";
            case "2":
            case "TUE":
                return "TUE";
            case "3":
            case "WED":
                return "WED";
            case "4":
            case "THU":
                return "THU";
            case "5":
            case "FRI":
                return "FRI";
            case "6":
            case "SAT":
                return "SAT";
            default:
                return null;
        }
    }

    private String normalizeCronExpression(String cron) {
        if (!StringUtils.hasText(cron)) {
            return "0 0 2 ? * MON";
        }
        String normalized = cron.trim().replaceAll("\\s+", " ");
        String[] parts = normalized.split(" ");
        if (parts.length == 5) {
            return "0 " + normalized;
        }
        return normalized;
    }

    @Data
    private static class DraftUpdateRequest {
        private String id;
        private String title;
        private String content;
    }

    @Data
    private static class DraftSaveRequest {
        private List<String> ids;
        private Long knowledgeId;
    }

    @Data
    private static class StartCrawlRequest {
        /**
         * 可选：本次任务起始URL，支持绝对URL或相对路径
         */
        private String startUrl;

        /**
         * 可选：本次任务请求间隔（毫秒）
         */
        private Integer requestIntervalMs;

        /**
         * 可选：本次运行模式
         */
        private String runMode;
    }
}
