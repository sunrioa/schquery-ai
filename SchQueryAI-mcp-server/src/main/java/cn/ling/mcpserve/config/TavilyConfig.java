package cn.ling.mcpserve.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Tavily API 配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "tavily")
public class TavilyConfig {

    /**
     * Tavily API Key
     */
    private String apiKey;

    /**
     * API 基础 URL
     */
    private String apiUrl = "https://api.tavily.com";

    /**
     * 搜索深度：basic 或 advanced
     */
    private String searchDepth = "advanced";

    /**
     * 最大搜索结果数量
     */
    private Integer maxResults = 5;

    /**
     * 是否包含原始内容
     */
    private Boolean includeRawContent = false;

    /**
     * 是否包含图片
     */
    private Boolean includeImages = false;
}

