package cn.ling.mcpserve.service;

import cn.ling.mcpserve.config.TavilyConfig;
import cn.ling.mcpserve.model.TavilyRequest;
import cn.ling.mcpserve.model.TavilyResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Tavily 搜索服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TavilyService {

    private final TavilyConfig tavilyConfig;
    private final RestTemplateBuilder restTemplateBuilder;

    public TavilyResponse search(String query) {
        return search(query, null, null);
    }

    public TavilyResponse search(String query, Integer maxResults, String searchDepth) {
        log.info("执行 Tavily 搜索，查询：{}", query);

        TavilyRequest request = TavilyRequest.builder()
                .apiKey(tavilyConfig.getApiKey())
                .query(query)
                .searchDepth(searchDepth != null ? searchDepth : tavilyConfig.getSearchDepth())
                .maxResults(maxResults != null ? maxResults : tavilyConfig.getMaxResults())
                .includeAnswer(true)
                .includeRawContent(tavilyConfig.getIncludeRawContent())
                .includeImages(tavilyConfig.getIncludeImages())
                .build();

        try {
            RestTemplate restTemplate = restTemplateBuilder
                    .setConnectTimeout(Duration.ofSeconds(30))
                    .setReadTimeout(Duration.ofSeconds(30))
                    .build();

            TavilyResponse response = restTemplate.postForObject(
                    tavilyConfig.getApiUrl() + "/search",
                    request,
                    TavilyResponse.class
            );
            if (response != null) {
                log.info("搜索成功，返回 {} 条结果", response.getResults() != null ? response.getResults().size() : 0);
            }
            return response;
        } catch (Exception e) {
            log.error("Tavily 搜索失败：{}", e.getMessage(), e);
            throw new RuntimeException("搜索失败：" + e.getMessage(), e);
        }
    }

    public String formatResponse(TavilyResponse response) {
        if (response == null) {
            return "未找到搜索结果";
        }

        StringBuilder sb = new StringBuilder();

        if (response.getAnswer() != null && !response.getAnswer().isEmpty()) {
            sb.append("【AI 答案】\n");
            sb.append(response.getAnswer());
            sb.append("\n\n");
        }

        if (response.getResults() != null && !response.getResults().isEmpty()) {
            sb.append("【搜索结果】\n");
            for (int i = 0; i < response.getResults().size(); i++) {
                TavilyResponse.SearchResult result = response.getResults().get(i);
                sb.append(String.format("%d. %s\n", i + 1, result.getTitle()));
                sb.append(String.format("   URL: %s\n", result.getUrl()));
                sb.append(String.format("   摘要: %s\n", result.getContent()));
                if (result.getScore() != null) {
                    sb.append(String.format("   相关性: %.2f\n", result.getScore()));
                }
                sb.append("\n");
            }
        }

        if (response.getImages() != null && !response.getImages().isEmpty()) {
            sb.append("【相关图片】\n");
            for (String image : response.getImages()) {
                sb.append(image).append("\n");
            }
        }

        return sb.toString();
    }
}
