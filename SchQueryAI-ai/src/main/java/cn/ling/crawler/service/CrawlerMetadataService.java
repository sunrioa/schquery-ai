package cn.ling.crawler.service;

import cn.ling.crawler.enums.SourceTypeEnum;
import cn.ling.crawler.service.CrawlerConfigService.CrawlerConfig;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 爬虫元数据生成服务
 */
@Slf4j
@Service
public class CrawlerMetadataService {

    /**
     * 为爬取的页面生成元数据
     */
    public String generatePageMetadata(String url, String title, String content,
                                      CrawlerConfig config) {
        Map<String, Object> metadata = new LinkedHashMap<>();

        // ===== 基本信息 =====
        metadata.put("source", "crawler");
        metadata.put("sourceUrl", url);
        metadata.put("sourceType", SourceTypeEnum.CRAWLER_FETCH.getValue());
        metadata.put("title", title);
        metadata.put("fetchTime", LocalDateTime.now().toString());

        // ===== 站点信息 =====
        metadata.put("site", extractDomain(config.getBaseUrl()));
        metadata.put("type", "page");

        // ===== 分类信息（从 URL 提取）=====
        metadata.put("category", extractCategory(url));
        metadata.put("subCategory", extractSubCategory(url));

        // ===== 内容统计 =====
        metadata.put("contentLength", content != null ? content.length() : 0);
        metadata.put("imageCount", 0); // 爬取时暂不统计

        return new Gson().toJson(metadata);
    }

    /**
     * 从 URL 提取域名
     */
    private String extractDomain(String url) {
        if (url == null) {
            return "";
        }
        try {
            String host = new java.net.URL(url).getHost();
            return host != null ? host : url;
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * 从 URL 提取一级分类
     */
    private String extractCategory(String url) {
        if (url == null) {
            return "其他";
        }
        String lower = url.toLowerCase();

        if (lower.contains("/jwc/") || lower.contains("/jwc/") || lower.contains("教务")) {
            return "教务处";
        }
        if (lower.contains("/lib/") || lower.contains("/library/") || lower.contains("图书馆")) {
            return "图书馆";
        }
        if (lower.contains("/xsc/") || lower.contains("/student/") || lower.contains("学生")) {
            return "学生处";
        }
        if (lower.contains("/hq/") || lower.contains("/dept/") || lower.contains("部门")) {
            return "行政部门";
        }
        if (lower.contains("/yjsy/") || lower.contains("/graduate/") || lower.contains("研究生")) {
            return "研究生院";
        }
        if (lower.contains("/news/") || lower.contains("/xwzx/") || lower.contains("新闻")) {
            return "新闻中心";
        }
        if (lower.contains("/notice/") || lower.contains("/tzgg/") || lower.contains("通知公告")) {
            return "通知公告";
        }

        return "其他";
    }

    /**
     * 从 URL 提取二级分类
     */
    private String extractSubCategory(String url) {
        if (url == null) {
            return "";
        }
        String lower = url.toLowerCase();

        // 根据一级分类返回二级分类
        String category = extractCategory(url);
        switch (category) {
            case "教务处":
                if (lower.contains("/exam")) return "考试安排";
                if (lower.contains("/schedule")) return "课表查询";
                if (lower.contains("/score")) return "成绩查询";
                break;
            case "学生处":
                if (lower.contains("/scholarship")) return "奖学金";
                if (lower.contains("/activity")) return "学生活动";
                break;
        }

        return "";
    }
}
