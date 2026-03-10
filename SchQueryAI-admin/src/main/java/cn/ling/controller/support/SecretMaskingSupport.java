package cn.ling.controller.support;

import org.springframework.util.StringUtils;

/**
 * 敏感字段脱敏与回传识别工具。
 */
public final class SecretMaskingSupport {

    private static final String MASK_BLOCK = "********";
    private static final String MASK_SUFFIX = "（已加密）";

    private SecretMaskingSupport() {
    }

    /**
     * 对密钥脱敏，保留少量前后缀，便于管理员识别是否已配置。
     */
    public static String maskSecret(String raw) {
        if (!StringUtils.hasText(raw)) {
            return raw;
        }
        String secret = raw.trim();
        int len = secret.length();
        if (len <= 8) {
            return MASK_BLOCK + MASK_SUFFIX;
        }
        int prefix = Math.min(4, Math.max(2, len / 6));
        int suffix = Math.min(3, Math.max(2, len / 8));
        if (prefix + suffix >= len) {
            return MASK_BLOCK + MASK_SUFFIX;
        }
        return secret.substring(0, prefix) + MASK_BLOCK + secret.substring(len - suffix) + MASK_SUFFIX;
    }

    /**
     * 判断前端传入是否为脱敏占位符。
     */
    public static boolean isMaskedPlaceholder(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String text = value.trim();
        return text.endsWith(MASK_SUFFIX) || text.contains(MASK_BLOCK);
    }

    /**
     * 更新时合并敏感字段：如果前端传回脱敏占位符，则保留数据库旧值。
     */
    public static String mergeForUpdate(String incoming, String persisted) {
        if (isMaskedPlaceholder(incoming)) {
            return persisted;
        }
        return incoming;
    }
}

