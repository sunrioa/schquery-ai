package cn.ling.mcpserve.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Tavily 搜索请求
 */
@Data
@Builder
public class TavilyRequest {

    /**
     * API Key
     */
    @JsonProperty("api_key")
    private String apiKey;

    /**
     * 搜索查询
     */
    private String query;

    /**
     * 搜索深度：basic 或 advanced
     */
    @JsonProperty("search_depth")
    private String searchDepth;

    /**
     * 是否包含答案
     */
    @JsonProperty("include_answer")
    private Boolean includeAnswer;

    /**
     * 是否包含原始内容
     */
    @JsonProperty("include_raw_content")
    private Boolean includeRawContent;

    /**
     * 最大搜索结果数量
     */
    @JsonProperty("max_results")
    private Integer maxResults;

    /**
     * 包含的域名列表
     */
    @JsonProperty("include_domains")
    private List<String> includeDomains;

    /**
     * 排除的域名列表
     */
    @JsonProperty("exclude_domains")
    private List<String> excludeDomains;

    /**
     * 是否包含图片
     */
    @JsonProperty("include_images")
    private Boolean includeImages;
}

