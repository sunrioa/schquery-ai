package cn.ling.service.mcp;

import cn.ling.service.SysConfigService;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态 MCP 客户端管理（读取 sys_config：chat.default.mcpServers）
 */
@Slf4j
@Service
public class DynamicMcpClientService {

    private static final String KEY_MCP_SERVERS = "chat.default.mcpServers";

    @Value("${spring.ai.mcp.client.request-timeout:300s}")
    private Duration requestTimeout;

    private final SysConfigService sysConfigService;

    /**
     * key: MCP服务URL, value: McpSyncClient
     */
    private final Map<String, McpSyncClient> mcpClientCache = new ConcurrentHashMap<>();

    public DynamicMcpClientService(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    public List<String> getMcpServerUrls() {
        String raw = sysConfigService.getConfigValue(KEY_MCP_SERVERS);
        List<String> urls = new ArrayList<>();
        if (!StringUtils.hasText(raw)) {
            return urls;
        }
        for (String part : raw.split(",")) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            urls.add(part.trim());
        }
        return urls;
    }

    public List<McpSyncClient> getMcpClients() {
        List<String> urls = getMcpServerUrls();
        List<McpSyncClient> clients = new ArrayList<>();
        if (urls.isEmpty()) {
            return clients;
        }

        for (String url : urls) {
            try {
                McpSyncClient client = mcpClientCache.computeIfAbsent(url, this::createMcpClient);
                if (client != null) {
                    clients.add(client);
                }
            } catch (Exception e) {
                log.warn("连接MCP服务失败: {}, err={}", url, e.getMessage());
            }
        }
        return clients;
    }

    public void clearCache() {
        mcpClientCache.values().forEach(client -> {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        });
        mcpClientCache.clear();
    }

    public List<McpSyncClient> refreshMcpClients() {
        clearCache();
        return getMcpClients();
    }

    private McpSyncClient createMcpClient(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            String baseUrl = normalizeBaseUrl(url);
            HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(baseUrl).build();
            McpSyncClient client = McpClient.sync(transport)
                    .requestTimeout(requestTimeout)
                    .build();
            client.initialize();
            return client;
        } catch (Exception e) {
            log.warn("创建MCP客户端失败: {}, err={}", url, e.getMessage());
            return null;
        }
    }

    private static String normalizeBaseUrl(String url) {
        String normalized = url.trim();
        if (normalized.endsWith("/sse")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        if (normalized.endsWith("/mcp")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}

