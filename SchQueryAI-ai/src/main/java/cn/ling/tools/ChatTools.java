package cn.ling.tools;

import cn.ling.rpc.SearchRpc;
import cn.ling.utils.HtmlUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * AI聊天工具类
 * 提供搜索和网页信息获取功能，供AI模型调用
 */
@Slf4j
@Component
public class ChatTools {

    /**
     * 搜索RPC服务
     * 用于执行网络搜索和网页信息获取
     */
    @Resource
    private SearchRpc searchRpc;

    /**
     * 联网搜索工具
     * <p>
     * 该工具方法允许AI模型执行网络搜索查询，获取相关搜索结果。
     * 返回的结果包含搜索到的页面标题、描述和URL地址。
     * 如果搜索结果信息不足，可以进一步调用getPageInformation工具访问具体URL获取详细内容。
     *
     * @param query 搜索查询关键词，可以是中文或英文
     * @return 搜索结果的格式化字符串，包含主要搜索结果和相关信息
     * @throws IllegalArgumentException 当查询参数为空或null时
     * @throws RuntimeException 当搜索服务调用失败时
     *
     * @Tool 注解说明：
     * - name: 工具名称，用于AI模型识别和调用
     * - description: 工具功能描述，帮助AI模型理解何时使用该工具
     */
    @Tool(
            name = "联网查询",
            description = "联网搜索内容，返回搜索结果，结果为初始查询的结果，包含相关页面描述和页面url地址,如果信息不足，可以调用工具访问url地址"
    )
    public String onlineQuery(@ToolParam(description = "联网查询") String query) {
        // 参数校验
        if (query == null || query.trim().isEmpty()) {
            log.warn("联网查询失败：查询参数为空");
            throw new IllegalArgumentException("查询参数不能为空");
        }

        // 记录搜索开始
        log.info("开始执行联网查询，查询内容: {}", query);
        long startTime = System.currentTimeMillis();

        try {
            // 调用搜索服务获取数据
            String data = getData(query);

            // 计算耗时
            long duration = System.currentTimeMillis() - startTime;

            // 记录搜索结果和性能信息
            log.info("联网查询完成，查询内容: {}, 耗时: {}ms, 结果长度: {} 字符",
                    query, duration, data.length());

            return data;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("联网查询失败，查询内容: {}, 耗时: {}ms, 错误信息: {}",
                    query, duration, e.getMessage(), e);
            throw new RuntimeException("联网查询服务调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取网页详细信息工具
     * <p>
     * 该工具方法允许AI模型访问指定的URL地址，获取网页的详细内容信息。
     * 网页内容会经过HTML清理处理，提取主要文本内容，去除广告、导航等无关信息。
     * 通常在联网搜索结果信息不够详细时使用，用于获取特定页面的完整内容。
     *
     * @param url 要访问的网页URL地址，必须是有效的HTTP或HTTPS链接
     * @return 网页内容的主要文本信息，经过清理和格式化
     * @throws IllegalArgumentException 当URL参数为空或格式无效时
     * @throws RuntimeException 当网页访问失败或内容解析失败时
     *
     * @Tool 注解说明：
     * - name: 工具名称，用于AI模型识别和调用
     * - description: 工具功能描述，说明该工具可以访问网页并获取信息
     */
    @Tool(
            name = "获取网页信息",
            description = "访问某个url地址，获取网页信息"
    )
    public String getPageInformation(@ToolParam(description = "url地址") String url) {
        // 参数校验
        if (url == null || url.trim().isEmpty()) {
            log.warn("获取网页信息失败：URL参数为空");
            throw new IllegalArgumentException("URL参数不能为空");
        }

        // 基本URL格式验证
        if (!url.matches("^https?://.*")) {
            log.warn("获取网页信息失败：URL格式无效 - {}", url);
            throw new IllegalArgumentException("URL格式无效，必须以http://或https://开头");
        }

        // 记录访问开始
        log.info("开始获取网页信息，URL: {}", url);
        long startTime = System.currentTimeMillis();

        try {
            // 调用搜索服务获取网页原始内容
            String rawInfo = searchRpc.getInfo(url);

            // 记录原始数据获取情况
            long fetchDuration = System.currentTimeMillis() - startTime;
            log.info("网页原始内容获取完成，URL: {}, 耗时: {}ms, 原始内容长度: {} 字符",
                    url, fetchDuration, rawInfo != null ? rawInfo.length() : 0);

            if (rawInfo == null || rawInfo.trim().isEmpty()) {
                log.warn("网页内容为空，URL: {}", url);
                return "未获取到有效的网页内容";
            }

            // 提取主要内容
            long parseStartTime = System.currentTimeMillis();
            String mainContent = HtmlUtils.extractMainContent(rawInfo);
            long parseDuration = System.currentTimeMillis() - parseStartTime;
            long totalDuration = System.currentTimeMillis() - startTime;

            // 记录解析结果和性能信息
            log.info("网页内容解析完成，URL: {}, 总耗时: {}ms(获取{}ms + 解析{}ms), 清理后内容长度: {} 字符",
                    url, totalDuration, fetchDuration, parseDuration,
                    mainContent != null ? mainContent.length() : 0);

            // 控制台输出（保持原有逻辑）
            System.out.println("获取网页信息结果：" + mainContent);

            return mainContent;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("获取网页信息失败，URL: {}, 耗时: {}ms, 错误信息: {}",
                    url, duration, e.getMessage(), e);
            throw new RuntimeException("网页信息获取失败: " + e.getMessage(), e);
        }
    }



    /**
     * 获取搜索数据的业务接口方法
     * 整合搜索调用和结果处理，为上层服务提供简洁的接口
     *
     * @param query 搜索关键词，可以是中文或英文
     * @return 格式化后的搜索结果字符串，适合传递给AI模型处理
     * @throws IllegalArgumentException 当搜索参数为空时
     * @throws RuntimeException 当搜索服务调用失败或结果处理异常时
     */
    private String getData(String query) {
        // 创建Logger实例用于default方法中的日志记录
        Logger log = LoggerFactory.getLogger(getClass());

        // 参数校验
        if (query == null || query.trim().isEmpty()) {
            log.warn("搜索失败：查询参数为空");
            throw new IllegalArgumentException("查询参数不能为空");
        }

        // 记录搜索开始
        log.info("开始执行搜索，查询内容: {}", query);
        long startTime = System.currentTimeMillis();

        try {
            // 调用搜索接口
            SearchRpc.SearchResult result = searchRpc.doSearch(query);

            // 记录搜索结果状态
            long searchDuration = System.currentTimeMillis() - startTime;
            if (result == null) {
                log.warn("搜索结果为空，查询内容: {}, 耗时: {}ms", query, searchDuration);
                return "未找到搜索结果";
            }

            // 记录搜索成功信息
            SearchRpc.SearchResult.SearchMetadata metadata = result.getSearch_metadata();
            if (metadata != null) {
                log.info("搜索成功完成，查询内容: {}, 耗时: {}ms, 搜索ID: {}, 状态: {}",
                        query, searchDuration, metadata.getId(), metadata.getStatus());
            } else {
                log.info("搜索完成，查询内容: {}, 耗时: {}ms, 无元数据", query, searchDuration);
            }

            // 提取并处理搜索结果
            long processStartTime = System.currentTimeMillis();
            String processedResult = extractRelevantInfoForLLM(result);
            long processDuration = System.currentTimeMillis() - processStartTime;
            long totalDuration = System.currentTimeMillis() - startTime;

            // 记录处理结果和性能信息
            log.info("搜索结果处理完成，查询内容: {}, 总耗时: {}ms(搜索{}ms + 处理{}ms), 结果长度: {} 字符",
                    query, totalDuration, searchDuration, processDuration,
                    processedResult.length());

            return processedResult;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("搜索失败，查询内容: {}, 耗时: {}ms, 错误信息: {}", query, duration, e.getMessage(), e);
            throw new RuntimeException("搜索服务调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 提取适合传递给LLM的有效信息
     * 从搜索结果中过滤出对LLM最有价值的信息，包括主要搜索结果、相关搜索建议等
     *
     * @param result 搜索结果对象
     * @return 格式化后的搜索结果字符串，适合AI模型理解和处理
     */
     private String extractRelevantInfoForLLM(SearchRpc.SearchResult result) {
        // 创建Logger实例用于default方法中的日志记录
        Logger log = LoggerFactory.getLogger(getClass());

        // 空值检查
        if (result == null) {
            log.warn("搜索结果为空，无法提取信息");
            return "未找到搜索结果";
        }

        // 记录信息提取开始
        log.debug("开始提取搜索结果信息，原始结果包含: 有机结果{}个, 相关搜索{}个, 相关视频{}个",
                result.getOrganic_results() != null ? result.getOrganic_results().size() : 0,
                result.getPeople_also_search_for() != null ? result.getPeople_also_search_for().size() : 0,
                result.getOrganic_results() != null && !result.getOrganic_results().isEmpty() ?
                        result.getOrganic_results().get(0).getRelated_videos() != null ?
                                result.getOrganic_results().get(0).getRelated_videos().size() : 0 : 0);

        StringBuilder sb = new StringBuilder();
        int processedResults = 0;

        try {
            // 添加搜索基本信息
            SearchRpc.SearchResult.SearchInformation searchInfo = result.getSearch_information();
            if (searchInfo != null && searchInfo.getQuery_displayed() != null) {
                String displayedQuery = searchInfo.getQuery_displayed();
                sb.append("搜索查询: ").append(displayedQuery).append("\n\n");
                log.debug("添加搜索查询信息: {}", displayedQuery);
            }

            // 添加搜索到的结果 - 这些是最有价值的信息
            List<SearchRpc.SearchResult.OrganicResult> organicResults = result.getOrganic_results();
            if (organicResults != null && !organicResults.isEmpty()) {
                sb.append("主要搜索结果:\n");
                int maxResults = Math.min(organicResults.size(), 5); // 限制前5个结果

                log.debug("开始处理主要搜索结果，共{}个，将处理前{}个", organicResults.size(), maxResults);

                for (int i = 0; i < maxResults; i++) {
                    SearchRpc.SearchResult.OrganicResult orgResult = organicResults.get(i);
                    sb.append(i + 1).append(". ").append(orgResult.getTitle()).append("\n");

                    // 添加摘要信息 - 这是LLM需要的关键内容
                    if (orgResult.getSnippet() != null && !orgResult.getSnippet().isEmpty()) {
                        // 清理HTML标签和多余空格
                        String cleanSnippet = cleanText(orgResult.getSnippet());
                        sb.append("   ").append(cleanSnippet).append("\n");
                        log.trace("添加搜索结果{}的摘要，长度: {} 字符", i + 1, cleanSnippet.length());
                    }

                    // 添加链接
                    if (orgResult.getLink() != null) {
                        sb.append("   链接: ").append(orgResult.getLink()).append("\n");
                        log.trace("添加搜索结果{}的链接: {}", i + 1, orgResult.getLink());
                    }

                    // 添加位置信息（如果有）
                    if (orgResult.getPosition() != null) {
                        sb.append("   排名: ").append(orgResult.getPosition()).append("\n");
                    }

                    sb.append("\n");
                    processedResults++;
                }

                log.info("主要搜索结果处理完成，成功处理{}个结果", processedResults);
            } else {
                log.warn("未找到主要搜索结果");
            }

            // 添加相关搜索建议
            List<SearchRpc.SearchResult.RelatedSearch> relatedSearches = result.getPeople_also_search_for();
            if (relatedSearches != null && !relatedSearches.isEmpty()) {
                sb.append("相关搜索:\n");
                int maxRelated = Math.min(relatedSearches.size(), 3); // 限制前3个

                log.debug("开始处理相关搜索建议，共{}个，将显示前{}个", relatedSearches.size(), maxRelated);

                for (int i = 0; i < maxRelated; i++) {
                    SearchRpc.SearchResult.RelatedSearch related = relatedSearches.get(i);
                    sb.append("- ").append(related.getText()).append("\n");
                    log.trace("添加相关搜索{}: {}", i + 1, related.getText());
                }

                log.info("相关搜索建议处理完成，包含{}个建议", maxRelated);
            } else {
                log.debug("未找到相关搜索建议");
            }

            String finalResult = sb.toString();
            log.info("搜索结果信息提取完成，总长度: {} 字符，处理的主要结果: {} 个",
                    finalResult.length(), processedResults);

            return finalResult;

        } catch (Exception e) {
            log.error("搜索结果信息提取失败，已处理{}个结果，错误信息: {}", processedResults, e.getMessage(), e);
            return "搜索结果处理出现异常: " + e.getMessage();
        }
    }

    /**
     * 清理文本，移除HTML标签和多余空格
     * 确保传递给LLM的文本内容干净、易读
     *
     * @param text 原始文本，可能包含HTML标签或多余空白字符
     * @return 清理后的纯文本内容
     */
    private String cleanText(String text) {
        // 创建Logger实例用于private方法中的日志记录
        Logger log = LoggerFactory.getLogger(getClass());

        // 空值检查
        if (text == null) {
            log.trace("清理文本时遇到null值，返回空字符串");
            return "";
        }

        try {
            // 移除HTML标签（简化版）
            String cleaned = text.replaceAll("<[^>]*>", "");
            log.trace("移除HTML标签，原始长度: {}, 移除后长度: {}", text.length(), cleaned.length());

            // 移除多余空格和换行，并清理首尾空白
            String finalCleaned = cleaned.replaceAll("\\s+", " ").trim();

            // 记录清理过程
            if (finalCleaned.length() != text.length()) {
                log.debug("文本清理完成，原始长度: {}, 清理后长度: {}, 压缩率: {}%",
                        text.length(), finalCleaned.length(),
                        !text.isEmpty() ? (100 * (text.length() - finalCleaned.length()) / text.length()) : 0);
            }

            return finalCleaned;

        } catch (Exception e) {
            log.error("文本清理失败，使用原文本。错误信息: {}", e.getMessage(), e);
            // 异常情况下返回原文本
            return text.trim();
        }
    }
}
