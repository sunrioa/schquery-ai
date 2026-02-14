package cn.ling.crawler.service;

import cn.ling.domain.pojo.KnowledgeInfo;
import cn.ling.service.KnowledgeInfoService;
import cn.ling.service.SysConfigService;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 爬虫配置服务
 */
@Service
public class CrawlerConfigService {

    @Resource
    private SysConfigService sysConfigService;

    @Resource
    private KnowledgeInfoService knowledgeInfoService;

    /**
     * 获取配置（直接从数据库查询，不使用默认值）
     */
    public CrawlerConfig getConfig() {
        // 查询所有爬虫配置
        Boolean enabled = getBooleanFromDb("enabled");
        String baseUrl = getStringFromDb("base.url");
        String startUrl = getStringFromDb("start.url");
        Integer maxPages = getIntFromDb("max.pages");
        Integer contentMinLength = getIntFromDb("content.min.length");
        Integer contentMaxLength = getIntFromDb("content.max.length");
        Long knowledgeId = getLongFromDb("knowledge.id");
        Boolean scheduleEnabled = getBooleanFromDb("schedule.enabled");
        String scheduleCron = getStringFromDb("schedule.cron");

        // 根据knowledgeId查询knowledgeName
        String knowledgeName = null;
        if (knowledgeId != null) {
            try {
                knowledgeName = knowledgeInfoService.lambdaQuery()
                        .eq(KnowledgeInfo::getId, knowledgeId)
                        .one()
                        .getKname();
            } catch (Exception e) {
                // 忽略错误
            }
        }

        return CrawlerConfig.builder()
                .enabled(enabled)
                .baseUrl(baseUrl)
                .startUrl(startUrl)
                .maxPages(maxPages)
                .contentMinLength(contentMinLength)
                .contentMaxLength(contentMaxLength)
                .knowledgeId(knowledgeId)
                .knowledgeName(knowledgeName)
                .scheduleEnabled(scheduleEnabled)
                .scheduleCron(scheduleCron)
                .build();
    }

    /**
     * 更新配置
     */
    public void updateConfig(String key, String value) {
        sysConfigService.upsertConfig("crawler." + key, value, "crawler." + key, "爬虫配置");
    }

    private String getStringFromDb(String key) {
        return sysConfigService.getConfigValue("crawler." + key);
    }

    private Integer getIntFromDb(String key) {
        String value = sysConfigService.getConfigValue("crawler." + key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private Long getLongFromDb(String key) {
        String value = sysConfigService.getConfigValue("crawler." + key);
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private Boolean getBooleanFromDb(String key) {
        String value = sysConfigService.getConfigValue("crawler." + key);
        if (value != null) {
            return Boolean.parseBoolean(value);
        }
        return null;
    }

    /**
     * 配置类
     */
    @Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CrawlerConfig {
        private Boolean enabled;
        private String baseUrl;
        private String startUrl;
        private Integer maxPages;
        private Integer contentMinLength;
        private Integer contentMaxLength;
        private Long knowledgeId;
        private String knowledgeName;
        private Boolean scheduleEnabled;
        private String scheduleCron;
    }
}
