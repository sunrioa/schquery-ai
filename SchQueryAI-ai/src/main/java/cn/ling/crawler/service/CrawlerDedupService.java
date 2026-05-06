package cn.ling.crawler.service;

import cn.ling.crawler.domain.CrawlerUrlRecord;
import cn.ling.crawler.mapper.CrawlerUrlRecordMapper;
import cn.ling.crawler.util.CrawlerUrlUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 爬虫去重服务
 */
@Slf4j
@Service
public class CrawlerDedupService {

    @Resource
    private CrawlerUrlRecordMapper urlRecordMapper;

    /**
     * 内存缓存（已访问 URL）
     */
    private final Set<String> visitedUrlsCache = ConcurrentHashMap.newKeySet();

    /**
     * 检查 URL 是否已成功访问（内存缓存 + 数据库）
     * 只将成功的记录视为"已访问"，失败和跳过的记录可以重试
     */
    public boolean isUrlVisited(String url) {
        String canonicalUrl = CrawlerUrlUtils.canonicalize(url);
        if (!StringUtils.hasText(canonicalUrl)) {
            return false;
        }

        // 先查内存缓存
        if (visitedUrlsCache.contains(canonicalUrl)) {
            return true;
        }

        Set<String> urlCandidates = CrawlerUrlUtils.buildMatchCandidates(url);
        if (urlCandidates.isEmpty()) {
            return false;
        }

        Long count = urlRecordMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CrawlerUrlRecord>()
                        .in(CrawlerUrlRecord::getUrl, urlCandidates)
                        .eq(CrawlerUrlRecord::getStatus, 1)
                        .isNull(CrawlerUrlRecord::getErrorMsg)
        );
        boolean visited = count != null && count > 0;
        if (visited) {
            visitedUrlsCache.add(canonicalUrl);
        }
        return visited;
    }

    /**
     * 标记 URL 已访问
     * 如果记录已存在则更新，不存在则插入
     */
    public void markUrlVisited(String url, String title, Integer status, String errorMsg) {
        String canonicalUrl = CrawlerUrlUtils.canonicalize(url);
        if (!StringUtils.hasText(canonicalUrl)) {
            return;
        }

        // 加入内存缓存
        if (status != null && status == 1) {
            visitedUrlsCache.add(canonicalUrl);
        }

        // 写入数据库（使用 saveOrUpdate 避免唯一约束冲突）
        try {
            Set<String> urlCandidates = CrawlerUrlUtils.buildMatchCandidates(url);
            CrawlerUrlRecord record = CrawlerUrlRecord.builder()
                    .url(canonicalUrl)
                    .title(title)
                    .status(status)
                    .errorMsg(errorMsg)
                    .visitTime(java.time.LocalDateTime.now())
                    .build();

            // 检查是否已存在
            List<CrawlerUrlRecord> existingRecords = urlRecordMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CrawlerUrlRecord>()
                            .in(CrawlerUrlRecord::getUrl, urlCandidates)
            );
            CrawlerUrlRecord existing = existingRecords.stream()
                    .filter(item -> canonicalUrl.equals(item.getUrl()))
                    .findFirst()
                    .orElse(existingRecords.isEmpty() ? null : existingRecords.get(0));

            if (existing != null) {
                // 更新现有记录
                existing.setUrl(canonicalUrl);
                if (StringUtils.hasText(title)) {
                    existing.setTitle(title);
                }
                existing.setStatus(status);
                existing.setErrorMsg(errorMsg);
                existing.setVisitTime(java.time.LocalDateTime.now());
                urlRecordMapper.updateById(existing);
                log.debug("更新 URL 访问记录: {}, status={}", canonicalUrl, status);
            } else {
                // 插入新记录
                urlRecordMapper.insert(record);
                log.debug("新增 URL 访问记录: {}, status={}", canonicalUrl, status);
            }
        } catch (Exception e) {
            log.warn("记录 URL 访问失败: {}", canonicalUrl, e);
        }
    }

    /**
     * 清空缓存
     */
    public void clearCache() {
        visitedUrlsCache.clear();
    }

    /**
     * 清空所有数据（内存缓存 + 数据库）
     */
    public void clearAll() {
        // 清空内存缓存
        visitedUrlsCache.clear();

        // 清空数据库记录
        try {
            urlRecordMapper.deleteAll();
            log.info("已清空爬虫 URL 记录表");
        } catch (Exception e) {
            log.warn("清空数据库记录失败: {}", e.getMessage());
        }
    }
}
