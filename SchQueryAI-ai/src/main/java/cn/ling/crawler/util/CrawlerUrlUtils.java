package cn.ling.crawler.util;

import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 爬虫 URL 规范化工具
 * 用于把同一页面的不同 URL 表示收敛成稳定主键，避免重复抓取和重复入库。
 */
public final class CrawlerUrlUtils {

    private static final Set<String> TRACKING_QUERY_KEYS = Set.of(
            "spm", "from", "source", "sharetoken", "share_token",
            "tdsourcetag", "_t", "_timestamp", "timestamp",
            "sessionid", "jsessionid", "phpsessid"
    );

    private CrawlerUrlUtils() {
    }

    /**
     * 生成 URL 匹配候选集。
     * 主要用于兼容历史上尚未规范化的旧数据。
     */
    public static Set<String> buildMatchCandidates(String rawUrl) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (StringUtils.hasText(rawUrl)) {
            candidates.add(rawUrl.trim());
        }

        String canonicalUrl = canonicalize(rawUrl);
        if (StringUtils.hasText(canonicalUrl)) {
            candidates.add(canonicalUrl);
            addTrailingSlashVariant(canonicalUrl, candidates);
        }
        return candidates;
    }

    /**
     * URL 规范化规则：
     * 1. scheme/host 统一小写
     * 2. 去掉 fragment
     * 3. 去掉默认端口
     * 4. 去掉常见追踪参数并排序 query
     * 5. 去掉多余尾斜杠
     * 6. 去掉 path 中的 jsessionid
     */
    public static String canonicalize(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return "";
        }

        String trimmed = rawUrl.trim();
        try {
            URI uri = new URI(trimmed);
            if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                return trimmed;
            }

            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            int port = normalizePort(scheme, uri.getPort());
            String path = normalizePath(uri.getRawPath());
            String query = normalizeQuery(uri.getRawQuery());

            StringBuilder builder = new StringBuilder();
            builder.append(scheme).append("://").append(host);
            if (port != -1) {
                builder.append(":").append(port);
            }
            builder.append(path);
            if (StringUtils.hasText(query)) {
                builder.append('?').append(query);
            }
            return builder.toString();
        } catch (Exception e) {
            return trimmed;
        }
    }

    private static int normalizePort(String scheme, int port) {
        if (port == -1) {
            return -1;
        }
        if (("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443)) {
            return -1;
        }
        return port;
    }

    private static String normalizePath(String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            return "/";
        }

        String normalized = rawPath
                .replaceAll("(?i);jsessionid=[^/?#]*", "")
                .replaceAll("/{2,}", "/");

        if (!StringUtils.hasText(normalized)) {
            return "/";
        }
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalizeQuery(String rawQuery) {
        if (!StringUtils.hasText(rawQuery)) {
            return null;
        }

        List<String> segments = new ArrayList<>();
        for (String segment : rawQuery.split("&")) {
            if (!StringUtils.hasText(segment)) {
                continue;
            }

            String key = segment;
            String value = "";
            int index = segment.indexOf('=');
            if (index >= 0) {
                key = segment.substring(0, index);
                value = segment.substring(index + 1);
            }

            if (isTrackingQueryKey(key)) {
                continue;
            }
            if (!StringUtils.hasText(key) && !StringUtils.hasText(value)) {
                continue;
            }

            segments.add(StringUtils.hasText(value) ? key + "=" + value : key);
        }

        if (segments.isEmpty()) {
            return null;
        }

        segments.sort(String.CASE_INSENSITIVE_ORDER);
        return String.join("&", segments);
    }

    private static boolean isTrackingQueryKey(String rawKey) {
        if (!StringUtils.hasText(rawKey)) {
            return false;
        }
        String key = rawKey.trim().toLowerCase(Locale.ROOT);
        return key.startsWith("utm_") || TRACKING_QUERY_KEYS.contains(key);
    }

    private static void addTrailingSlashVariant(String canonicalUrl, Set<String> candidates) {
        try {
            URI uri = new URI(canonicalUrl);
            if (StringUtils.hasText(uri.getRawQuery())) {
                return;
            }

            String path = uri.getRawPath();
            if (!StringUtils.hasText(path) || "/".equals(path)) {
                return;
            }

            if (path.endsWith("/")) {
                candidates.add(canonicalUrl.substring(0, canonicalUrl.length() - 1));
            } else {
                candidates.add(canonicalUrl + "/");
            }
        } catch (Exception ignored) {
        }
    }
}
