package cn.ling.crawler.service;

import cn.ling.crawler.service.CrawlerConfigService.CrawlerConfig;
import cn.ling.crawler.vo.CrawlerStatusVO;
import cn.ling.service.DocumentChunksService;
import cn.ling.service.DocumentsService;
import cn.ling.service.SyncService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 学校官网爬虫服务
 */
@Slf4j
@Service
public class WebsiteCrawlerService {

    @Resource
    private CrawlerConfigService crawlerConfigService;

    @Resource
    private CrawlerDedupService dedupService;

    @Resource
    private CrawlerMetadataService metadataService;

    @Resource
    private DocumentsService documentsService;

    @Resource
    private DocumentChunksService documentChunksService;

    @Resource
    private SyncService syncService;

    /**
     * 爬虫运行状态
     */
    private volatile boolean running = false;
    private final Set<String> pendingUrls = ConcurrentHashMap.newKeySet();
    private final Set<String> visitedUrlSet = ConcurrentHashMap.newKeySet();

    /**
     * 获取爬虫完整状态信息
     */
    public CrawlerStatusVO getStatus() {
        return new CrawlerStatusVO(running, visitedUrlSet.size(), pendingUrls.size());
    }

    /**
     * 停止爬虫
     */
    public void stop() {
        if (running) {
            running = false;
            log.info("爬虫已停止 - 已访问页面: {}, 待处理队列: {}",
                    visitedUrlSet.size(), pendingUrls.size());
        } else {
            log.info("爬虫未在运行，无需停止");
        }
    }

    /**
     * 获取待处理 URL 数量
     */
    public int getPendingUrlCount() {
        return pendingUrls.size();
    }

    /**
     * 获取已访问 URL 数量
     */
    public int getVisitedUrlCount() {
        return visitedUrlSet.size();
    }

    /**
     * 清空缓存（只清空内存，保留数据库历史记录）
     */
    public void clearCache() {
        pendingUrls.clear();
        visitedUrlSet.clear();
        dedupService.clearCache();
    }

    /**
     * 清空所有数据（包括数据库记录）
     */
    public void clearAll() {
        pendingUrls.clear();
        visitedUrlSet.clear();
        dedupService.clearAll(); // 清空内存和数据库
    }

    /**
     * 启动爬虫
     */
    public void manualCrawl() {
        CrawlerConfig config = crawlerConfigService.getConfig();
        if (!config.getEnabled()) {
            log.info("爬虫功能已禁用，跳过本次爬取");
            return;
        }

        log.info("开始手动爬虫任务...");
        running = true;

        // 只清空内存缓存（本次任务状态），不清空数据库历史记录
        pendingUrls.clear();
        visitedUrlSet.clear();
        log.info("已清空本次任务缓存");

        try {
            // 1. 初始化：添加起始URL到待处理队列
            String startUrl = config.getBaseUrl() + config.getStartUrl();
            pendingUrls.add(startUrl);
            log.info("添加起始 URL 到待处理队列: {}", startUrl);

            // 2. 循环处理待处理队列，直到队列为空或达到限制
            crawlSite();

        } finally {
            running = false;
            log.info("手动爬虫任务完成 - 已访问页面: {}, 待处理队列: {}",
                    visitedUrlSet.size(), pendingUrls.size());
        }
    }

    /**
     * 爬取指定网站（循环处理待处理队列）
     */
    private void crawlSite() {
        while (running && !pendingUrls.isEmpty()) {
            // 动态获取配置（支持运行中更新）
            CrawlerConfig config = crawlerConfigService.getConfig();

            // 检查是否达到最大页面数限制
            if (visitedUrlSet.size() >= config.getMaxPages()) {
                log.warn("已达到最大页面数限制，停止爬虫");
                break;
            }

            // 从队列中取出一个 URL（使用迭代器安全遍历）
            String url = pendingUrls.iterator().next();
            pendingUrls.remove(url);

            // 检查本次任务是否已处理（防止循环）
            if (visitedUrlSet.contains(url)) {
                continue;
            }

            // 标记为本次任务已访问
            visitedUrlSet.add(url);

            // 检查数据库中是否已访问（决定是否保存数据）
            boolean wasVisited = dedupService.isUrlVisited(url);
            if (wasVisited) {
                log.info("页面已存在，仅提取链接: {}", url);
            }

            // 处理页面（获取内容和链接）
            CrawlResult result = processPage(url, config);
            if (result.isSkipped()) {
                log.debug("跳过页面: {}", url);
                continue;
            }

            // 只保存新页面到知识库
            if (result.isSuccess() && !wasVisited) {
                saveToKnowledgeBase(url, result.getTitle(), result.getContent(), config);
            }

            // 将发现的链接添加到待处理队列（无论是否已访问）
            Set<String> discoveredUrls = result.getDiscoveredUrls();
            if (discoveredUrls != null && !discoveredUrls.isEmpty()) {
                log.debug("发现 {} 个新链接，添加到待处理队列", discoveredUrls.size());
                pendingUrls.addAll(discoveredUrls);
            }
        }
    }

    /**
     * 处理单个页面
     */
    private CrawlResult processPage(String url, CrawlerConfig config) {
        try {
            log.info("处理页面: url={}", url);

            // 1. 获取页面（Jsoup）
            Document doc = fetchPage(url, config);
            if (doc == null) {
                return CrawlResult.failed("获取页面失败");
            }

            // 2. 提取页面标题
            String title = extractTitle(doc);
            if (title.isEmpty()) {
                return CrawlResult.failed("无法提取标题");
            }

            // 3. 提取所有文本内容
            String content = extractContent(doc);

            // 4. 提取所有链接
            Set<String> links = extractLinks(doc, config);

            // 5. 标记 URL 已访问
            dedupService.markUrlVisited(url, title, 1, null);

            // 6. 返回成功结果（包含发现的链接）
            return CrawlResult.success(title, content, links, doc);
        } catch (Exception e) {
            log.error("处理页面失败: {}", url, e);
            return CrawlResult.failed(e.getMessage());
        }
    }

    /**
     * 获取页面（Jsoup）
     */
    private Document fetchPage(String url, CrawlerConfig config) {
        try {
            return Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(10000)
                    .ignoreHttpErrors(true)
                    .get();
        } catch (IOException e) {
            log.warn("获取页面失败: {} - {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * 提取页面标题
     */
    private String extractTitle(Document doc) {
        Element titleElement = doc.selectFirst("title");
        if (titleElement != null) {
            String title = titleElement.text();
            if (title.length() > 200) {
                title = title.substring(0, 200);
            }
            return title;
        }
        return "";
    }

    /**
     * 提取所有文本内容
     */
    private String extractContent(Document doc) {
        StringBuilder content = new StringBuilder();
        for (Element element : doc.select("body p, body td, body li, body h1, body h2, body h3, body h4, body h5, body h6")) {
            content.append(element.text());
            content.append("\n");
        }

        return content.toString();
    }

    /**
     * 提取所有链接
     */
    private Set<String> extractLinks(Document doc, CrawlerConfig config) {
        Set<String> links = new HashSet<>();

        int totalLinks = 0;
        int skippedLinks = 0;

        for (Element link : doc.select("a[href]")) {
            totalLinks++;
            String href = link.attr("href");
            if (!StringUtils.hasText(href)) {
                skippedLinks++;
                continue;
            }

            // 转换为绝对URL
            String absoluteUrl = link.absUrl("href");

            // 检查是否应该访问该链接
            if (!shouldVisitUrl(absoluteUrl, config)) {
                skippedLinks++;
                continue;
            }

            // 检查是否已在待处理队列或本次已访问（避免重复添加）
            if (pendingUrls.contains(absoluteUrl) || visitedUrlSet.contains(absoluteUrl)) {
                skippedLinks++;
                continue;
            }

            // 添加到链接列表（不检查数据库历史记录，以便重新爬取）
            links.add(absoluteUrl);
        }

        log.info("链接提取完成 - 总链接数: {}, 有效链接: {}, 跳过链接: {}",
                totalLinks, links.size(), skippedLinks);

        return links;
    }

    /**
     * 检查是否应该访问该 URL
     */
    private boolean shouldVisitUrl(String url, CrawlerConfig config) {
        // 1. 检查是否为空
        if (!StringUtils.hasText(url)) {
            return false;
        }

        // 2. 检查协议和常见非内容链接（放在域名检查之前，避免解析异常）
        String lowerUrl = url.toLowerCase();
        if (!lowerUrl.startsWith("http://") && !lowerUrl.startsWith("https://")) {
            return false; // 只处理 HTTP/HTTPS 协议
        }
        if (lowerUrl.contains("javascript:") || lowerUrl.contains("mailto:") ||
                lowerUrl.contains("#") || lowerUrl.endsWith(".pdf") ||
                lowerUrl.endsWith(".zip") || lowerUrl.endsWith(".rar") ||
                lowerUrl.endsWith(".exe") || lowerUrl.endsWith(".jpg") ||
                lowerUrl.endsWith(".png") || lowerUrl.endsWith(".gif") ||
                lowerUrl.endsWith(".jpeg") || lowerUrl.endsWith(".bmp") ||
                lowerUrl.endsWith(".svg") || lowerUrl.endsWith(".webp")) {
            return false;
        }

        // 3. 检查是否为同一域名
        try {
            String baseUrlDomain = new java.net.URL(config.getBaseUrl()).getHost();
            String urlDomain = new java.net.URL(url).getHost();
            if (!baseUrlDomain.equals(urlDomain)) {
                return false;
            }
        } catch (Exception e) {
            log.debug("解析域名失败，跳过链接: {}", url);
            return false;
        }

        // 4. 检查 URL 长度限制
        return url.length() <= 500;
    }

    /**
     * 存入知识库
     */
    private void saveToKnowledgeBase(String url, String title, String content, CrawlerConfig config) {
        try {
            // 生成元数据 JSON 字符串
            String metadataJson = metadataService.generatePageMetadata(url, title, content, config);

            // 将 JSON 字符串转换为 Map
            Gson gson = new Gson();
            TypeToken<Map<String, Object>> typeToken =
                    new TypeToken<>() {};
            Map<String, Object> metadata = gson.fromJson(metadataJson, typeToken.getType());

            // 调用同步服务保存到知识库（异步处理文档分块和向量化）
            syncService.saveCrawlerContent(config.getKnowledgeId(), content, metadata,
                    documentsService, documentChunksService);

            log.info("已存入知识库: url={}, 标题={}, {} 字符", url, title, content.length());
        } catch (Exception e) {
            log.error("存入知识库失败: {}", url, e);
        }
    }

    /**
     * 爬取结果类
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class CrawlResult {
        private final boolean success;
        private final Set<String> discoveredUrls;
        private final String message;
        private final String title;
        private final String content;
        private final Document doc;

        static CrawlResult success(String title, String content, Set<String> urls, Document doc) {
            return new CrawlResult(true, urls, "成功", title, content, doc);
        }

        static CrawlResult failed(String reason) {
            return new CrawlResult(false, new HashSet<>(), reason, "", "", null);
        }

        public boolean isSkipped() {
            return !success && (discoveredUrls == null || discoveredUrls.isEmpty());
        }

    }
}
