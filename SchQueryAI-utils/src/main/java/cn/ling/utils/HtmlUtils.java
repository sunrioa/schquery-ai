package cn.ling.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * HTML内容清理工具类
 * 用于从HTML页面中提取干净的文本内容，同时保留基本结构
 * 支持清理脚本、样式、广告等无关内容，保留主要文本信息
 */
@Slf4j
public class HtmlUtils {

    /**
     * 多个空白字符正则表达式
     * 用于合并连续的空格、换行符、制表符等
     */
    private static final Pattern MULTIPLE_SPACES_PATTERN = Pattern.compile("\\s+");

    /**
     * 需要完全移除的JavaScript、CSS和相关内容的正则表达式数组
     * 这些内容对AI理解网页主要内容没有帮助，应该被清理掉
     */
    private static final Pattern[] PATTERNS_TO_REMOVE = {
        Pattern.compile("(?is)<script[^>]*>.*?</script>"),    // JavaScript代码
        Pattern.compile("(?is)<style[^>]*>.*?</style>"),      // CSS样式表
        Pattern.compile("(?is)<noscript[^>]*>.*?</noscript>"), // noscript内容
        Pattern.compile("(?is)<!--.*?-->"),                   // HTML注释
        Pattern.compile("(?is)<iframe[^>]*>.*?</iframe>"),    // iframe嵌入内容
        Pattern.compile("(?is)<object[^>]*>.*?</object>"),     // object对象
        Pattern.compile("(?is)<embed[^>]*>.*?</embed>"),      // embed嵌入内容
        Pattern.compile("(?is)<link[^>]*>.*?</link>"),        // link标签（用于样式表等）
        Pattern.compile("(?is)<meta[^>]*>.*?</meta>"),         // meta标签（用于元数据）
    };

  /**
     * 需要完全移除的HTML标签及其内容
     * 这些标签通常包含与主要内容无关的脚本、样式、导航等内容
     */
    private static final String[] TAGS_TO_REMOVE_FULL = {
        "script",      // JavaScript脚本
        "style",       // CSS样式
        "noscript",    // 无脚本时的显示内容
        "iframe",      // 内嵌框架
        "object",      // 嵌入对象
        "embed",       // 嵌入内容
        "link",        // 链接标签（通常用于CSS）
        "meta",        // 元数据标签
        "!DOCTYPE html", // 文档类型声明
        "head",        // 头部信息
        "footer"       // 底部信息
    };

    /**
     * 需要保留内容但清理属性HTML标签
     * 这些标签包含重要文本信息，但不需要复杂的CSS类、ID等属性
     */
    private static final String[] TAGS_TO_CLEAN_ATTRS = {
        // 标题标签
        "h1", "h2", "h3", "h4", "h5", "h6",
        // 内容结构标签
        "p", "div", "span", "section", "article", "main",
        // 列表标签
        "ul", "ol", "li",
        // 表格标签
        "table", "tr", "td", "th", "tbody", "thead", "tfoot",
        // 文本格式标签
        "a", "strong", "em", "b", "i", "u"
    };

    /**
     * 需要完全移除的特定网页元素
     * 通常是导航栏、广告、社交媒体分享按钮等非主要内容
     */
    private static final String[] SPECIFIC_TAGS_TO_REMOVE = {
        "nav",                                    // 导航栏
        "header",                                 // 页面头部
        "aside",                                  // 侧边栏
        "div class=\"bdsharebuttonbox\"",        // 百度分享按钮
        "div class=\"wrapper\"",                  // 包装器
        "div class=\"inner\""                     // 内层包装
    };

    /**
     * 主入口方法：清理HTML内容，提取主要文本并保留基本结构
     * 按照标准流程对HTML内容进行多阶段清理，提取对AI有价值的文本信息
     *
     * @param htmlContent 原始HTML内容字符串
     * @return 清理后的主要内容文本，保留基本的HTML结构
     */
    public static String extractMainContent(String htmlContent) {
        // 空值检查
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            log.debug("输入HTML内容为空，返回默认提示信息");
            return "未获取到有效内容";
        }

        // 记录清理开始
        log.info("开始HTML内容清理，原始长度: {} 字符", htmlContent.length());
        long startTime = System.currentTimeMillis();

        try {
            String cleaned = htmlContent;
            int originalLength = cleaned.length();

            // 1. 移除JavaScript、CSS等脚本和样式内容
            long step1Start = System.currentTimeMillis();
            cleaned = removeScriptsAndStyles(cleaned);
            long step1Duration = System.currentTimeMillis() - step1Start;
            log.debug("第1步 - 移除脚本样式: 原始{}字符 → 清理后{}字符, 耗时: {}ms",
                    originalLength, cleaned.length(), step1Duration);

            // 2. 移除导航、头部、底部等页面结构元素
            long step2Start = System.currentTimeMillis();
            cleaned = removeNavigationElements(cleaned);
            long step2Duration = System.currentTimeMillis() - step2Start;
            log.debug("第2步 - 移除导航元素: 清理前{}字符 → 清理后{}字符, 耗时: {}ms",
                    cleaned.length(), cleaned.length(), step2Duration);

            // 3. 清理保留标签的属性，保留基本结构
            long step3Start = System.currentTimeMillis();
            cleaned = cleanAttributes(cleaned);
            long step3Duration = System.currentTimeMillis() - step3Start;
            log.debug("第3步 - 清理标签属性: 清理前{}字符 → 清理后{}字符, 耗时: {}ms",
                    cleaned.length(), cleaned.length(), step3Duration);

            // 4. 清理多余空白字符，规范文本格式
            String finalCleaned = MULTIPLE_SPACES_PATTERN.matcher(cleaned).replaceAll(" ");

            return finalCleaned.trim();

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("HTML内容清理异常，耗时: {}ms, 使用基础清理方法。错误信息: {}", duration, e.getMessage(), e);

            // 异常情况下，使用基础清理方法作为降级处理
            return basicHtmlCleanup(htmlContent);
        }
    }

  /**
     * 第1步：移除JavaScript、CSS等脚本和样式内容
     * 清理对AI理解文本内容没有帮助的技术性内容
     *
     * @param htmlContent 待清理的HTML内容
     * @return 移除脚本和样式后的HTML内容
     */
    private static String removeScriptsAndStyles(String htmlContent) {
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            log.trace("移除脚本和样式方法收到空内容");
            return htmlContent;
        }

        long startTime = System.currentTimeMillis();
        String cleaned = htmlContent;
        int originalLength = cleaned.length();
        int removedPatterns = 0;

        try {
            // 按照优先级逐一应用清理模式
            for (Pattern pattern : PATTERNS_TO_REMOVE) {
                String beforeMatch = cleaned;
                cleaned = pattern.matcher(cleaned).replaceAll(" ");
                if (!beforeMatch.equals(cleaned)) {
                    removedPatterns++;
                    log.trace("应用清理模式{}，内容长度变化: {} → {} 字符",
                            pattern.pattern(), beforeMatch.length(), cleaned.length());
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            int finalLength = cleaned.length();
            int removedChars = originalLength - finalLength;

            log.debug("脚本和样式清理完成: 原始{}字符 → 清理后{}字符, 移除{}字符, 应用{}个清理模式, 耗时: {}ms",
                    originalLength, finalLength, removedChars, removedPatterns, duration);

            return cleaned;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("脚本和样式清理异常，耗时: {}ms, 返回原内容。错误信息: {}", duration, e.getMessage(), e);
            return htmlContent;
        }
    }

    /**
     * 第2步：移除导航、头部、底部等页面结构元素
     * 清理页面的非主要内容区域，如导航栏、页脚、侧边栏等
     *
     * @param htmlContent 待清理的HTML内容
     * @return 移除导航元素后的HTML内容
     */
    private static String removeNavigationElements(String htmlContent) {
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            log.trace("移除导航元素方法收到空内容");
            return htmlContent;
        }

        long startTime = System.currentTimeMillis();
        String cleaned = htmlContent;
        int originalLength = cleaned.length();
        int removedTags = 0;

        try {
            // 移除完全不需要的标签及其内容
            log.debug("开始移除完整的标签及其内容，共{}个标签类型", TAGS_TO_REMOVE_FULL.length);
            for (String tag : TAGS_TO_REMOVE_FULL) {
                String beforeMatch = cleaned;
                // 使用非贪婪匹配，避免误删除
                cleaned = cleaned.replaceAll("(?i)<" + tag + "[^>]*>.*?</" + tag + ">", " ");
                if (!beforeMatch.equals(cleaned)) {
                    removedTags++;
                    log.trace("移除标签{}，内容长度变化: {} → {} 字符", tag, beforeMatch.length(), cleaned.length());
                }
            }

            // 移除特定的页面结构元素（通常是开始和结束标签分开处理）
            log.debug("开始移除特定页面结构元素，共{}个元素类型", SPECIFIC_TAGS_TO_REMOVE.length);
            for (String tag : SPECIFIC_TAGS_TO_REMOVE) {
                String beforeMatch = cleaned;
                cleaned = cleaned.replaceAll("(?i)<" + tag + "[^>]*>", " ");
                cleaned = cleaned.replaceAll("(?i)</" + tag + ">", " ");
                if (!beforeMatch.equals(cleaned)) {
                    removedTags++;
                    log.trace("移除特定元素{}，内容长度变化: {} → {} 字符", tag, beforeMatch.length(), cleaned.length());
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            int finalLength = cleaned.length();
            int removedChars = originalLength - finalLength;

            log.debug("导航元素清理完成: 原始{}字符 → 清理后{}字符, 移除{}字符, 清理{}个标签, 耗时: {}ms",
                    originalLength, finalLength, removedChars, removedTags, duration);

            return cleaned;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("导航元素清理异常，耗时: {}ms, 返回原内容。错误信息: {}", duration, e.getMessage(), e);
            return htmlContent;
        }
    }

    /**
     * 第3步：清理标签属性
     * 保留重要的HTML标签结构，但移除复杂的CSS类、ID、样式等属性
     * 使内容更适合AI理解和处理
     *
     * @param htmlContent 待清理的HTML内容
     * @return 清理属性后的HTML内容
     */
    private static String cleanAttributes(String htmlContent) {
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            log.trace("清理标签属性方法收到空内容");
            return htmlContent;
        }

        long startTime = System.currentTimeMillis();
        String cleaned = htmlContent;
        int originalLength = cleaned.length();
        int cleanedTags = 0;

        try {
            log.debug("开始清理标签属性，共{}个标签类型", TAGS_TO_CLEAN_ATTRS.length);
            for (String tag : TAGS_TO_CLEAN_ATTRS) {
                String beforeMatch = cleaned;
                // 保留开始标签，但移除所有属性
                cleaned = cleaned.replaceAll("(?i)<" + tag + "[^>]*>", "<" + tag + ">");
                // 结束标签保持不变（但为了保险起见也处理一下）
                cleaned = cleaned.replaceAll("(?i)</" + tag + ">", "</" + tag + ">");
                if (!beforeMatch.equals(cleaned)) {
                    cleanedTags++;
                    log.trace("清理标签{}属性，内容长度变化: {} → {} 字符", tag, beforeMatch.length(), cleaned.length());
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            int finalLength = cleaned.length();
            int removedChars = originalLength - finalLength;

            log.debug("标签属性清理完成: 原始{}字符 → 清理后{}字符, 移除{}字符, 清理{}个标签, 耗时: {}ms",
                    originalLength, finalLength, removedChars, cleanedTags, duration);

            return cleaned;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("标签属性清理异常，耗时: {}ms, 返回原内容。错误信息: {}", duration, e.getMessage(), e);
            return htmlContent;
        }
    }

    /**
     * 基础HTML清理（备用方法）
     * 当主要清理流程出现异常时使用的简化清理方法
     * 只进行最基础的清理，确保系统稳定性
     *
     * @param htmlContent 原始HTML内容
     * @return 基础清理后的文本内容
     */
    private static String basicHtmlCleanup(String htmlContent) {
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            log.debug("基础清理方法收到空内容");
            return "";
        }

        long startTime = System.currentTimeMillis();
        log.info("启动基础HTML清理方法，原始长度: {} 字符", htmlContent.length());

        try {
            String cleaned = htmlContent;

            // 1. 移除JavaScript、CSS等不需要的内容（复用现有的模式）
            log.debug("基础清理：移除脚本和样式");
            for (Pattern pattern : PATTERNS_TO_REMOVE) {
                cleaned = pattern.matcher(cleaned).replaceAll(" ");
            }

            // 2. 保留基本标题和段落标签，为其添加换行以便阅读
            log.debug("基础清理：格式化基本标签");
            String[] tagsToKeep = {"h1", "h2", "h3", "h4", "h5", "h6", "p", "div", "span", "ul", "ol", "li", "table", "tr", "td", "th"};
            for (String tag : tagsToKeep) {
                // 在结束标签前添加换行，提高可读性
                cleaned = cleaned.replaceAll("(?i)</" + tag + ">", "\n</" + tag + ">");
                // 在开始标签后添加换行，提高可读性
                cleaned = cleaned.replaceAll("(?i)<" + tag + "([^>]*)>", "\n<" + tag + ">$1");
            }

            // 3. 清理多余空白字符
            log.debug("基础清理：规范空白字符");
            cleaned = MULTIPLE_SPACES_PATTERN.matcher(cleaned).replaceAll(" ");

            return cleaned.trim();

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("基础HTML清理异常，耗时: {}ms, 返回空字符串。错误信息: {}", duration, e.getMessage(), e);
            // 最极端的异常情况下返回空字符串
            return "";
        }
    }


}