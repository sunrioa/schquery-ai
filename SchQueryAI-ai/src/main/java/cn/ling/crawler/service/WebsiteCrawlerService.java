package cn.ling.crawler.service;

import cn.ling.crawler.domain.CrawlerDraftRecord;
import cn.ling.crawler.mapper.CrawlerDraftRecordMapper;
import cn.ling.crawler.util.CrawlerUrlUtils;
import cn.ling.domain.pojo.Documents;
import cn.ling.domain.pojo.DocumentChunks;
import cn.ling.crawler.service.CrawlerConfigService.CrawlerConfig;
import cn.ling.crawler.vo.CrawlerDraftResultVO;
import cn.ling.crawler.vo.CrawlerSaveResultVO;
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
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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

    @Resource
    private VectorStore qdrantVectorStore;

    @Resource
    private CrawlerDraftRecordMapper crawlerDraftRecordMapper;

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
     * 获取当前抓取结果草稿（按抓取时间倒序）
     */
    public List<CrawlerDraftResultVO> listDraftResults() {
        List<CrawlerDraftRecord> records = crawlerDraftRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CrawlerDraftRecord>()
                        .orderByDesc(CrawlerDraftRecord::getFetchTime)
                        .orderByDesc(CrawlerDraftRecord::getUpdatedTime)
        );
        List<CrawlerDraftResultVO> results = new ArrayList<>(records.size());
        for (CrawlerDraftRecord record : records) {
            results.add(toDraftVO(record));
        }
        return results;
    }

    /**
     * 获取草稿数量
     */
    public int getDraftResultCount() {
        Long count = crawlerDraftRecordMapper.selectCount(null);
        return count == null ? 0 : count.intValue();
    }

    /**
     * 更新草稿内容（标题/正文）
     */
    public CrawlerDraftResultVO updateDraftResult(String id, String title, String content) {
        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException("草稿ID不能为空");
        }
        CrawlerDraftRecord draft = crawlerDraftRecordMapper.selectById(id);
        if (draft == null) {
            throw new IllegalArgumentException("未找到对应抓取草稿");
        }
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("内容不能为空");
        }

        draft.setTitle(StringUtils.hasText(title) ? title.trim() : draft.getTitle());
        draft.setContent(content.trim());
        draft.setContentLength(draft.getContent().length());
        draft.setEdited(1);
        draft.setUpdatedTime(LocalDateTime.now());
        crawlerDraftRecordMapper.updateById(draft);
        return toDraftVO(draft);
    }

    /**
     * 清空抓取草稿
     */
    public void clearDraftResults() {
        crawlerDraftRecordMapper.delete(null);
    }

    /**
     * 保存选中的抓取草稿到知识库（触发分块+向量化）
     */
    public CrawlerSaveResultVO saveDraftResults(List<String> ids, Long knowledgeId) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("请选择要保存的抓取结果");
        }

        CrawlerConfig config = crawlerConfigService.getConfig();
        Long finalKnowledgeId = knowledgeId != null ? knowledgeId : config.getKnowledgeId();
        if (finalKnowledgeId == null) {
            throw new IllegalArgumentException("未配置知识库，请先在爬虫配置中选择知识库");
        }

        int submittedCount = 0;
        int skippedCount = 0;
        List<String> failedItems = new ArrayList<>();
        List<CrawlerDraftResultVO> savedItems = new ArrayList<>();

        for (String id : ids.stream().filter(Objects::nonNull).distinct().toList()) {
            CrawlerDraftRecord draft = crawlerDraftRecordMapper.selectById(id);
            if (draft == null) {
                skippedCount++;
                failedItems.add("未找到草稿：" + id);
                continue;
            }
            if (!StringUtils.hasText(draft.getContent())) {
                skippedCount++;
                failedItems.add("内容为空：" + draft.getUrl());
                continue;
            }

            try {
                String normalizedUrl = CrawlerUrlUtils.canonicalize(draft.getUrl());
                saveToKnowledgeBase(normalizedUrl, draft.getTitle(), draft.getContent(), config, finalKnowledgeId);
                dedupService.markUrlVisited(normalizedUrl, draft.getTitle(), 1, null);

                draft.setUrl(normalizedUrl);
                draft.setSaved(1);
                draft.setSavedTime(LocalDateTime.now());
                draft.setKnowledgeId(finalKnowledgeId);
                draft.setUpdatedTime(LocalDateTime.now());
                crawlerDraftRecordMapper.updateById(draft);
                savedItems.add(toDraftVO(draft));
                submittedCount++;
            } catch (Exception e) {
                skippedCount++;
                failedItems.add(draft.getUrl() + "：" + e.getMessage());
                log.error("保存抓取草稿失败: url={}", draft.getUrl(), e);
            }
        }

        return CrawlerSaveResultVO.builder()
                .submittedCount(submittedCount)
                .skippedCount(skippedCount)
                .knowledgeId(finalKnowledgeId)
                .failedItems(failedItems)
                .savedItems(savedItems)
                .build();
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
        clearDraftResults();
        dedupService.clearAll(); // 清空内存和数据库
    }

    /**
     * 启动爬虫
     */
    public synchronized boolean manualCrawl() {
        return manualCrawl(null, null);
    }

    /**
     * 启动爬虫（支持本次任务覆盖起始URL和抓取间隔）
     */
    public synchronized boolean manualCrawl(String startUrlOverride, Integer requestIntervalMsOverride) {
        if (running) {
            log.info("爬虫已在运行中，忽略重复启动");
            return false;
        }

        CrawlerConfig config = crawlerConfigService.getConfig();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            log.info("爬虫功能已禁用，跳过本次爬取");
            return false;
        }

        CrawlerConfig runConfig = CrawlerConfig.builder()
                .enabled(config.getEnabled())
                .baseUrl(config.getBaseUrl())
                .startUrl(config.getStartUrl())
                .maxPages(config.getMaxPages())
                .contentMinLength(config.getContentMinLength())
                .contentMaxLength(config.getContentMaxLength())
                .requestIntervalMs(config.getRequestIntervalMs())
                .strictVectorCheckEnabled(config.getStrictVectorCheckEnabled())
                .knowledgeId(config.getKnowledgeId())
                .knowledgeName(config.getKnowledgeName())
                .scheduleEnabled(config.getScheduleEnabled())
                .scheduleCron(config.getScheduleCron())
                .runMode(config.getRunMode())
                .build();

        if (requestIntervalMsOverride != null && requestIntervalMsOverride >= 0) {
            runConfig.setRequestIntervalMs(requestIntervalMsOverride);
        }

        String startUrl = resolveStartUrl(runConfig, startUrlOverride);

        log.info("开始手动爬虫任务...");
        running = true;

        // 只清空内存缓存（本次任务状态），不清空数据库历史记录
        pendingUrls.clear();
        visitedUrlSet.clear();
        log.info("已清空本次任务缓存");

        // 1. 初始化：添加起始URL到待处理队列
        pendingUrls.add(startUrl);
        log.info("添加起始 URL 到待处理队列: {}", startUrl);

        // 2. 异步执行爬取，避免阻塞接口
        CompletableFuture.runAsync(() -> {
            try {
                crawlSite(runConfig);
            } finally {
                running = false;
                if (CrawlerConfigService.RUN_MODE_ONCE.equals(crawlerConfigService.normalizeRunMode(runConfig.getRunMode()))) {
                    crawlerConfigService.setCrawlerEnabled(false);
                }
                log.info("手动爬虫任务完成 - 已访问页面: {}, 待处理队列: {}, 草稿数: {}",
                        visitedUrlSet.size(), pendingUrls.size(), getDraftResultCount());
            }
        });
        return true;
    }

    /**
     * 爬取指定网站（循环处理待处理队列）
     */
    private void crawlSite(CrawlerConfig config) {
        while (running && !pendingUrls.isEmpty()) {
            // 检查是否达到最大页面数限制
            Integer maxPages = config.getMaxPages();
            if (maxPages != null && maxPages > 0 && visitedUrlSet.size() >= maxPages) {
                log.warn("已达到最大页面数限制，停止爬虫");
                break;
            }

            // 从队列中取出一个 URL（使用迭代器安全遍历）
            String url = pendingUrls.iterator().next();
            pendingUrls.remove(url);
            url = CrawlerUrlUtils.canonicalize(url);
            if (!StringUtils.hasText(url)) {
                continue;
            }

            // 检查本次任务是否已处理（防止循环）
            if (visitedUrlSet.contains(url)) {
                continue;
            }

            // 历史已成功抓取过则直接跳过，避免跨任务重复访问
            if (dedupService.isUrlVisited(url)) {
                log.info("URL 历史已成功抓取，跳过重复访问: {}", url);
                applyRequestInterval(config.getRequestIntervalMs());
                continue;
            }

            // 已入库（且仍有分块记录）则不再重复访问页面
            boolean alreadyIndexed = isUrlIndexed(url, config);
            if (alreadyIndexed) {
                dedupService.markUrlVisited(url, null, 1, null);
                log.info("页面已入库，跳过重复访问: {}", url);
                applyRequestInterval(config.getRequestIntervalMs());
                continue;
            }

            // 标记为本次任务已访问
            visitedUrlSet.add(url);

            // 处理页面（获取内容和链接）
            CrawlResult result = processPage(url, config);
            if (!result.isSuccess()) {
                dedupService.markUrlVisited(url, result.getTitle(), 2, result.getMessage());
                log.debug("页面处理失败，跳过当前 URL: {}, reason={}", url, result.getMessage());
                applyRequestInterval(config.getRequestIntervalMs());
                continue;
            }

            // 将每个成功页面保存为草稿记录（每个页面一条）
            saveAsDraft(url, result.getTitle(), result.getContent());
            dedupService.markUrlVisited(url, result.getTitle(), 1, null);

            // 将发现的链接添加到待处理队列（无论是否已访问）
            Set<String> discoveredUrls = result.getDiscoveredUrls();
            if (discoveredUrls != null && !discoveredUrls.isEmpty()) {
                log.debug("发现 {} 个新链接，添加到待处理队列", discoveredUrls.size());
                pendingUrls.addAll(discoveredUrls);
            }

            applyRequestInterval(config.getRequestIntervalMs());
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
            if (!StringUtils.hasText(content)) {
                return CrawlResult.failed("页面内容为空");
            }

            // 3.1 根据配置控制内容长度
            content = normalizeContent(content, config);
            if (!StringUtils.hasText(content)) {
                return CrawlResult.failed("页面内容不满足长度要求");
            }

            // 4. 提取所有链接
            Set<String> links = extractLinks(doc, config);

            // 5. 返回成功结果（包含发现的链接）
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
     * 按配置裁剪内容长度
     */
    private String normalizeContent(String content, CrawlerConfig config) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.trim();

        Integer minLength = config.getContentMinLength();
        if (minLength != null && minLength > 0 && normalized.length() < minLength) {
            return "";
        }

        Integer maxLength = config.getContentMaxLength();
        if (maxLength != null && maxLength > 0 && normalized.length() > maxLength) {
            normalized = normalized.substring(0, maxLength);
        }
        return normalized;
    }

    /**
     * 解析本次任务起始URL（可覆盖配置）
     */
    private String resolveStartUrl(CrawlerConfig config, String startUrlOverride) {
        if (StringUtils.hasText(startUrlOverride)) {
            String override = startUrlOverride.trim();
            try {
                String absoluteUrl;
                if (override.startsWith("http://") || override.startsWith("https://")) {
                    absoluteUrl = override;
                } else {
                    if (!StringUtils.hasText(config.getBaseUrl())) {
                        throw new IllegalArgumentException("baseUrl 为空，无法解析相对起始URL");
                    }
                    absoluteUrl = new java.net.URL(new java.net.URL(config.getBaseUrl().trim()), override).toString();
                }

                absoluteUrl = CrawlerUrlUtils.canonicalize(absoluteUrl);
                java.net.URL parsed = new java.net.URL(absoluteUrl);
                String baseUrl = parsed.getProtocol() + "://" + parsed.getHost();
                if (parsed.getPort() != -1 && parsed.getPort() != parsed.getDefaultPort()) {
                    baseUrl += ":" + parsed.getPort();
                }
                String path = StringUtils.hasText(parsed.getPath()) ? parsed.getPath() : "/";
                if (StringUtils.hasText(parsed.getQuery())) {
                    path += "?" + parsed.getQuery();
                }

                config.setBaseUrl(baseUrl);
                config.setStartUrl(path);
                return absoluteUrl;
            } catch (Exception e) {
                throw new IllegalArgumentException("起始URL格式错误：" + override);
            }
        }

        if (!StringUtils.hasText(config.getBaseUrl()) || !StringUtils.hasText(config.getStartUrl())) {
            throw new IllegalStateException("爬虫配置不完整：baseUrl/startUrl 不能为空");
        }

        try {
            return CrawlerUrlUtils.canonicalize(
                    new java.net.URL(new java.net.URL(config.getBaseUrl().trim()), config.getStartUrl().trim()).toString()
            );
        } catch (Exception e) {
            throw new IllegalStateException("爬虫配置错误：baseUrl/startUrl 组合失败");
        }
    }

    /**
     * 控制请求频率，避免访问过快
     */
    private void applyRequestInterval(Integer intervalMs) {
        if (intervalMs == null || intervalMs <= 0) {
            return;
        }
        try {
            Thread.sleep(intervalMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("爬虫等待间隔被中断");
        }
    }

    /**
     * 新抓取内容保存为草稿（前端可编辑后再入库）
     */
    private void saveAsDraft(String url, String title, String content) {
        String normalizedUrl = CrawlerUrlUtils.canonicalize(url);
        if (!StringUtils.hasText(normalizedUrl) || !StringUtils.hasText(content)) {
            return;
        }
        synchronized (this) {
            Set<String> urlCandidates = CrawlerUrlUtils.buildMatchCandidates(url);
            List<CrawlerDraftRecord> existingRecords = crawlerDraftRecordMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CrawlerDraftRecord>()
                            .in(CrawlerDraftRecord::getUrl, urlCandidates)
            );
            CrawlerDraftRecord existing = existingRecords.stream()
                    .filter(record -> normalizedUrl.equals(record.getUrl()))
                    .findFirst()
                    .orElse(existingRecords.isEmpty() ? null : existingRecords.get(0));
            LocalDateTime now = LocalDateTime.now();
            if (existing != null) {
                existing.setUrl(normalizedUrl);
                existing.setTitle(title);
                existing.setContent(content);
                existing.setContentLength(content.length());
                existing.setFetchTime(now);
                existing.setUpdatedTime(now);
                existing.setSaved(0);
                existing.setSavedTime(null);
                existing.setKnowledgeId(null);
                existing.setEdited(0);
                crawlerDraftRecordMapper.updateById(existing);
                return;
            }

            String id = UUID.randomUUID().toString();
            CrawlerDraftRecord draft = CrawlerDraftRecord.builder()
                    .id(id)
                    .url(normalizedUrl)
                    .title(title)
                    .content(content)
                    .contentLength(content.length())
                    .fetchTime(now)
                    .updatedTime(now)
                    .saved(0)
                    .edited(0)
                    .savedTime(null)
                    .build();
            crawlerDraftRecordMapper.insert(draft);
            log.info("新增爬虫草稿: url={}, 内容长度={}", normalizedUrl, content.length());
        }
    }

    private CrawlerDraftResultVO toDraftVO(CrawlerDraftRecord record) {
        if (record == null) {
            return null;
        }
        return CrawlerDraftResultVO.builder()
                .id(record.getId())
                .url(record.getUrl())
                .title(record.getTitle())
                .content(record.getContent())
                .contentLength(record.getContentLength())
                .fetchTime(record.getFetchTime())
                .updatedTime(record.getUpdatedTime())
                .edited(record.getEdited() != null && record.getEdited() == 1)
                .saved(record.getSaved() != null && record.getSaved() == 1)
                .savedTime(record.getSavedTime())
                .knowledgeId(record.getKnowledgeId())
                .build();
    }

    /**
     * 判断URL是否已有可用索引（文档+分块）
     * 说明：若通过管理端删除文档，分块会一并删除；此时会被视为未索引，下一次可重新爬取为草稿。
     */
    private boolean isUrlIndexed(String url, CrawlerConfig config) {
        String normalizedUrl = CrawlerUrlUtils.canonicalize(url);
        if (!StringUtils.hasText(normalizedUrl)) {
            return false;
        }
        Set<String> urlCandidates = CrawlerUrlUtils.buildMatchCandidates(url);
        Long knowledgeId = config == null ? null : config.getKnowledgeId();
        List<Documents> matches = documentsService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Documents>()
                        .in(Documents::getSourceUrl, urlCandidates)
                        .eq(Documents::getSourceType, 2)
                        .eq(Documents::getProcessStatus, 2)
                        .eq(knowledgeId != null, Documents::getKnowledgeId, knowledgeId)
                        .and(wrapper -> wrapper.eq(Documents::getStatus, 1).or().isNull(Documents::getStatus))
                        .orderByDesc(Documents::getId)
        );
        Documents doc = matches.stream()
                .filter(item -> normalizedUrl.equals(item.getSourceUrl()))
                .findFirst()
                .orElse(matches.isEmpty() ? null : matches.get(0));
        if (doc == null || doc.getId() == null) {
            return false;
        }
        long chunkCount = documentChunksService.lambdaQuery()
                .eq(DocumentChunks::getDocumentId, doc.getId())
                .count();
        if (chunkCount <= 0) {
            return false;
        }

        // 默认走数据库快速判断，开启后再额外检查Qdrant中是否仍存在向量点位
        boolean strictVectorCheckEnabled = config != null && Boolean.TRUE.equals(config.getStrictVectorCheckEnabled());
        if (!strictVectorCheckEnabled) {
            return true;
        }

        boolean vectorExists = existsVectorInQdrant(doc.getId());
        if (!vectorExists) {
            log.warn("检测到文档记录存在但向量点位缺失，将重新抓取为草稿: url={}, documentId={}", url, doc.getId());
        }
        return vectorExists;
    }

    /**
     * 强校验：确认Qdrant中仍存在该文档对应向量点位
     */
    private boolean existsVectorInQdrant(Long documentId) {
        if (documentId == null) {
            return false;
        }
        try {
            SearchRequest numberFilterRequest = SearchRequest.builder()
                    .query("crawler_vector_exist_check")
                    .topK(1)
                    .similarityThreshold(-1.0d)
                    .filterExpression("WHERE document_id == " + documentId)
                    .build();
            List<org.springframework.ai.document.Document> docs = qdrantVectorStore.similaritySearch(numberFilterRequest);
            if (docs != null && !docs.isEmpty()) {
                return true;
            }

            SearchRequest stringFilterRequest = SearchRequest.builder()
                    .query("crawler_vector_exist_check")
                    .topK(1)
                    .similarityThreshold(-1.0d)
                    .filterExpression("WHERE document_id == '" + documentId + "'")
                    .build();
            docs = qdrantVectorStore.similaritySearch(stringFilterRequest);
            return docs != null && !docs.isEmpty();
        } catch (Exception e) {
            // 强校验失败时退回数据库快速判断，避免因向量库瞬时异常导致重复抓取
            log.warn("向量强校验失败，回退数据库判定: documentId={}, error={}", documentId, e.getMessage());
            return true;
        }
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
            String rawAbsoluteUrl = link.absUrl("href");
            String absoluteUrl = CrawlerUrlUtils.canonicalize(rawAbsoluteUrl);
            if (!StringUtils.hasText(absoluteUrl)) {
                skippedLinks++;
                continue;
            }

            // 检查是否应该访问该链接
            if (!shouldVisitUrl(absoluteUrl, config)) {
                skippedLinks++;
                continue;
            }

            // 检查是否已在待处理队列、本次已访问或历史已成功抓取（避免重复添加）
            if (pendingUrls.contains(absoluteUrl)
                    || visitedUrlSet.contains(absoluteUrl)
                    || dedupService.isUrlVisited(rawAbsoluteUrl)) {
                skippedLinks++;
                continue;
            }

            // 添加到链接列表
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
    private void saveToKnowledgeBase(String url, String title, String content, CrawlerConfig config, Long knowledgeId) {
        String normalizedUrl = CrawlerUrlUtils.canonicalize(url);

        // 生成元数据 JSON 字符串
        String metadataJson = metadataService.generatePageMetadata(normalizedUrl, title, content, config);

        // 将 JSON 字符串转换为 Map
        Gson gson = new Gson();
        TypeToken<Map<String, Object>> typeToken =
                new TypeToken<>() {};
        Map<String, Object> metadata = gson.fromJson(metadataJson, typeToken.getType());

        // 调用同步服务保存到知识库（异步处理文档分块和向量化）
        syncService.saveCrawlerContent(knowledgeId, content, metadata,
                documentsService, documentChunksService);

        log.info("已提交入库: url={}, 标题={}, {} 字符, knowledgeId={}", normalizedUrl, title, content.length(), knowledgeId);
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
