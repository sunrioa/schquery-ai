package cn.ling.service.chat;

import cn.ling.advisor.IntentRecognizerAdvisor;
import cn.ling.advisor.KnowledgeRagAdvisor;
import cn.ling.advisor.McpRagAdvisor;
import cn.ling.advisor.SensitiveFilterAdvisor;
import cn.ling.domain.pojo.ChatModel;
import cn.ling.prompt.Prompts;
import cn.ling.service.ChatModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态 ChatClient（按 chat_model.model_name 选择不同的 apiHost/apiKey）
 *
 * 说明：
 * - 预设(ChatPreset.model)只存模型名称；真正请求的 host/key 从 chat_model 中读取。
 * - 若 chat_model 未配置 apiHost/apiKey，则回退到默认 openAiChatClient（使用 application.yml 的 spring.ai.openai.*）。
 */
@Slf4j
@Service
public class DynamicChatClientService {

    private final ChatModelService chatModelService;
    private final ChatClient fallbackClient;

    private final KnowledgeRagAdvisor knowledgeRagAdvisor;
    private final McpRagAdvisor mcpRagAdvisor;
    private final SensitiveFilterAdvisor sensitiveFilterAdvisor;
    private final IntentRecognizerAdvisor intentRecognizerAdvisor;

    private final ConcurrentHashMap<String, CachedClient> cache = new ConcurrentHashMap<>();

    public DynamicChatClientService(
            ChatModelService chatModelService,
            @Qualifier("openAiChatClient") ChatClient fallbackClient,
            KnowledgeRagAdvisor knowledgeRagAdvisor,
            McpRagAdvisor mcpRagAdvisor,
            SensitiveFilterAdvisor sensitiveFilterAdvisor,
            IntentRecognizerAdvisor intentRecognizerAdvisor
    ) {
        this.chatModelService = chatModelService;
        this.fallbackClient = fallbackClient;
        this.knowledgeRagAdvisor = knowledgeRagAdvisor;
        this.mcpRagAdvisor = mcpRagAdvisor;
        this.sensitiveFilterAdvisor = sensitiveFilterAdvisor;
        this.intentRecognizerAdvisor = intentRecognizerAdvisor;
    }

    /**
     * 按模型名解析可用 ChatClient。
     */
    public ChatClient resolveClient(String modelName) {
        String normalized = trimToNull(modelName);
        if (!StringUtils.hasText(normalized)) {
            return fallbackClient;
        }

        ChatModel cfg = chatModelService.lambdaQuery()
                .eq(ChatModel::getCategory, "chat")
                .eq(ChatModel::getModelName, normalized)
                .orderByDesc(ChatModel::getPriority)
                .orderByDesc(ChatModel::getUpdateTime)
                .last("limit 1")
                .one();

        if (cfg == null) {
            return fallbackClient;
        }

        String apiHost = trimToNull(cfg.getApiHost());
        String apiKey = trimToNull(cfg.getApiKey());
        String apiUrl = trimToNull(cfg.getApiUrl());

        // 未配置 host/key：仍可用 fallbackClient + per-request options 改 model
        if (!StringUtils.hasText(apiHost) || !StringUtils.hasText(apiKey)) {
            return fallbackClient;
        }

        final String apiKeyHash = sha256Hex(apiKey);
        final LocalDateTime updateTime = cfg.getUpdateTime();

        CachedClient cached = cache.get(normalized);
        if (cached != null && cached.matches(apiHost, apiUrl, apiKeyHash, updateTime)) {
            return cached.client;
        }

        return cache.compute(normalized, (k, existing) -> {
            if (existing != null && existing.matches(apiHost, apiUrl, apiKeyHash, updateTime)) {
                return existing;
            }
            try {
                ChatClient built = buildClient(normalized, apiHost, apiUrl, apiKey);
                return new CachedClient(built, apiHost, apiUrl, apiKeyHash, updateTime);
            } catch (Exception e) {
                log.warn("构建动态ChatClient失败，回退默认客户端: model={}, err={}", normalized, e.getMessage());
                return existing; // 保留旧的（若存在），否则后续会回退
            }
        }).clientOrFallback(fallbackClient);
    }

    public void clearCache() {
        cache.clear();
    }

    private ChatClient buildClient(String modelName, String apiHost, String apiUrl, String apiKey) {
        String baseUrl = joinBaseUrl(apiHost, apiUrl);

        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

        OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder()
                .model(modelName)
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(defaultOptions)
                .build();

        return ChatClient.builder(chatModel)
                .defaultSystem(Prompts.SYSTEM_PROMPT)
                .defaultAdvisors(
                        knowledgeRagAdvisor,
                        mcpRagAdvisor,
                        intentRecognizerAdvisor,
                        sensitiveFilterAdvisor
                )
                .build();
    }

    private static String joinBaseUrl(String host, String url) {
        String h = host.trim();
        while (h.endsWith("/")) {
            h = h.substring(0, h.length() - 1);
        }
        if (!StringUtils.hasText(url)) {
            return h;
        }
        String u = url.trim();
        if (!u.startsWith("/")) {
            u = "/" + u;
        }
        return h + u;
    }

    private static String trimToNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String t = raw.trim();
        return t.isEmpty() ? null : t;
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // 极端情况下退化为长度标识（仍可用于缓存粗匹配）
            return "len:" + (raw == null ? 0 : raw.length());
        }
    }

    private static final class CachedClient {
        private final ChatClient client;
        private final String apiHost;
        private final String apiUrl;
        private final String apiKeyHash;
        private final LocalDateTime updateTime;

        private CachedClient(ChatClient client, String apiHost, String apiUrl, String apiKeyHash, LocalDateTime updateTime) {
            this.client = client;
            this.apiHost = apiHost;
            this.apiUrl = apiUrl;
            this.apiKeyHash = apiKeyHash;
            this.updateTime = updateTime;
        }

        private boolean matches(String apiHost, String apiUrl, String apiKeyHash, LocalDateTime updateTime) {
            return Objects.equals(this.apiHost, apiHost)
                    && Objects.equals(this.apiUrl, apiUrl)
                    && Objects.equals(this.apiKeyHash, apiKeyHash)
                    && Objects.equals(this.updateTime, updateTime);
        }

        private ChatClient clientOrFallback(ChatClient fallback) {
            return client != null ? client : fallback;
        }
    }
}

