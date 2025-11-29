package cn.ling.rpc;

import com.dtflys.forest.annotation.ForestClient;
import com.dtflys.forest.annotation.Get;
import com.dtflys.forest.annotation.Var;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 搜索RPC接口
 * 基于Forest HTTP客户端实现的搜索服务接口
 * 提供网络搜索和网页内容获取功能
 */
@Component
@ForestClient
public interface SearchRpc {

    /**
     * 执行网络搜索
     * 调用SerpApi搜索服务，获取相关的搜索结果
     *
     * @param query 搜索关键词
     * @return 搜索结果对象，包含搜索的元数据、结果列表、相关搜索等信息
     * @throws RuntimeException 当搜索服务调用失败或超时时
     *
     * @Get 注解说明：
     * - url: SerpApi搜索接口地址，使用配置文件中的搜索引擎和API密钥
     * - readTimeout: 读取超时30秒，适用于大搜索结果的获取
     * - connectTimeout: 连接超时5秒，确保快速响应网络连接
     */
    @Get(
            url = "https://serpapi.com/search?q={query}&engine=#{spring.ai.search.options.engine}&api_key=#{spring.ai.search.options.apiKey}",
            readTimeout = 30 * 1000,  // 读取超时时间：30秒
            connectTimeout = 5 * 1000  // 连接超时时间：5秒)
    )
    SearchResult doSearch(@Var("query") String query);

    /**
     * 获取网页详细内容
     * 直接访问指定的URL地址，获取网页的原始HTML内容
     *
     * @param url 目标网页URL地址
     * @return 网页的原始HTML内容字符串
     * @throws RuntimeException 当网页访问失败、超时或内容读取错误时
     *
     * @Get 注解说明：
     * - url: 动态URL地址，支持http和https协议
     * - readTimeout: 读取超时30秒，适用于内容较大的网页
     * - connectTimeout: 连接超时5秒，确保快速建立网络连接
     */
    @Get(
            url = "{url}",
            readTimeout = 30 * 1000,  // 读取超时时间：30秒
            connectTimeout = 5 * 1000  // 连接超时时间：5秒)
    )
    String getInfo(@Var("url") String url);


    /**
     * 搜索结果数据结构
     * 封装SerpApi返回的完整搜索结果信息
     * 包含搜索元数据、主要结果、相关搜索、分页信息等
     */
    @Data
    class SearchResult {
        private SearchMetadata search_metadata;
        private SearchParameters search_parameters;
        private SearchInformation search_information;
        private List<OrganicResult> organic_results;
        private List<RelatedSearch> people_also_search_for;
        private List<RelatedSearch> related_searches;
        private List<TopSearch> top_searches;
        private Pagination pagination;
        private SerpApiPagination serpapi_pagination;

        // 内部嵌套类 - 定义搜索结果的各个组成部分

        /**
         * 搜索元数据
         * 包含搜索请求的执行信息，如请求ID、状态、时间戳等
         */
        @Data
        public static class SearchMetadata {
            /** 搜索请求的唯一标识符 */
            private String id;
            /** 搜索请求的执行状态（成功、失败等） */
            private String status;
            /** JSON格式的API端点URL */
            private String json_endpoint;
            /** 搜索请求创建时间 */
            private String created_at;
            /** 搜索结果处理完成时间 */
            private String processed_at;
            /** 百度搜索的URL（如果适用） */
            private String baidu_url;
            /** 原始HTML文件路径 */
            private String raw_html_file;
            /** 搜索总耗时（秒） */
            private Double total_time_taken;
        }

        /**
         * 搜索参数
         * 包含执行搜索时使用的参数信息
         */
        @Data
        public static class SearchParameters {
            /** 搜索查询关键词 */
            private String q;
            /** 搜索过滤器 */
            private String f;
            /** 设备类型（桌面、移动等） */
            private String device;
            /** 使用的搜索引擎 */
            private String engine;
        }

        /**
         * 搜索信息
         * 包含搜索查询的显示信息
         */
        @Data
        public static class SearchInformation {
            /** 最终显示给用户的查询字符串 */
            private String query_displayed;
        }

        /**
         * 有机搜索结果
         * 主要的搜索结果条目，包含标题、链接、摘要等信息
         */
        @Data
        public static class OrganicResult {
            /** 搜索结果排名 */
            private Integer position;
            /** 搜索结果标题 */
            private String title;
            /** 搜索结果URL链接 */
            private String link;
            /** 搜索结果摘要描述 */
            private String snippet;
            /** 显示给用户的链接地址 */
            private String displayed_link;
            /** 相关视频列表（如果适用） */
            private List<RelatedVideo> related_videos;
        }

        /**
         * 相关视频信息
         * 与搜索结果相关的视频内容信息
         */
        @Data
        public static class RelatedVideo {
            /** 视频链接地址 */
            private String link;
            /** 视频缩略图地址 */
            private String image;
            /** 视频时长 */
            private String duration;
            /** 视频来源平台 */
            private String source;
        }

        /**
         * 相关搜索建议
         * 用户可能还会搜索的相关关键词
         */
        @Data
        public static class RelatedSearch {
            /** 相关搜索建议文本 */
            private String text;
            /** 相关搜索的链接地址 */
            private String link;
            /** SerpApi生成的相关搜索链接 */
            private String serpapi_link;
        }

        /**
         * 热门搜索
         * 当前热门或推荐搜索内容
         */
        @Data
        public static class TopSearch {
            /** 热门搜索排名 */
            private Integer position;
            /** 热门搜索内容 */
            private String text;
            /** 热门搜索链接 */
            private String link;
            /** 热门搜索标签 */
            private String tag;
            /** SerpApi生成的新浪搜索链接 */
            private String serpapi_link;
        }

        /**
         * 分页信息
         * 搜索结果分页相关信息
         */
        @Data
        public static class Pagination {
            /** 当前页码 */
            private Integer current;
            /** 下一页链接 */
            private String next;
            /** 其他页面链接映射 */
            private Map<String, String> other_pages;
        }

        /**
         * SerpApi分页信息
         * SerpApi服务提供的详细分页信息
         */
        @Data
        public static class SerpApiPagination {
            /** 当前页码 */
            private Integer current;
            /** 下一页完整链接 */
            private String next_link;
            /** 下一页参数 */
            private String next;
            /** 其他页面链接映射 */
            private Map<String, String> other_pages;
        }
    }
}