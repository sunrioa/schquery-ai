package cn.ling.mcpserve.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Tavily 搜索响应
 */
@Data
public class TavilyResponse {

    /**
     * 搜索查询
     */
    private String query;

    /**
     * AI 生成的答案
     */
    private String answer;

    /**
     * 搜索结果列表
     */
    private List<SearchResult> results;

    /**
     * 图片列表
     */
    private List<String> images;

    @Data
    public static class SearchResult {

        /**
         * 标题
         */
        private String title;

        /**
         * URL
         */
        private String url;

        /**
         * 内容摘要
         */
        private String content;

        /**
         * 原始内容
         */
        @JsonProperty("raw_content")
        private String rawContent;

        /**
         * 相关性分数
         */
        private Double score;

        /**
         * 发布日期
         */
        @JsonProperty("published_date")
        private String publishedDate;
    }
}

