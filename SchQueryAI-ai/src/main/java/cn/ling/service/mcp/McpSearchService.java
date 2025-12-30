package cn.ling.service.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP 网络检索封装（默认调用 tavilySearch 工具）
 */
@Slf4j
@Service
public class McpSearchService {

    private static final String MCP_NO_RESULT = "NO_RESULT";

    private final DynamicMcpClientService dynamicMcpClientService;

    public McpSearchService(DynamicMcpClientService dynamicMcpClientService) {
        this.dynamicMcpClientService = dynamicMcpClientService;
    }

    public String tavilySearch(String query) {
        if (!StringUtils.hasText(query)) {
            return "";
        }
        List<McpSyncClient> clients = dynamicMcpClientService.getMcpClients();
        if (clients.isEmpty()) {
            return "";
        }

        List<String> results = new ArrayList<>();
        for (McpSyncClient client : clients) {
            String text = callTavilySearch(client, query.trim());
            if (!StringUtils.hasText(text)) {
                continue;
            }
            String trimmed = text.trim();
            if (MCP_NO_RESULT.equalsIgnoreCase(trimmed)) {
                continue;
            }
            results.add(trimmed);
        }
        return results.isEmpty() ? "" : String.join("\n\n", results);
    }

    private String callTavilySearch(McpSyncClient client, String query) {
        if (client == null || !StringUtils.hasText(query)) {
            return "";
        }
        try {
            if (!clientHasTool(client, "tavilySearch")) {
                return "";
            }
            McpSchema.CallToolRequest req = new McpSchema.CallToolRequest("tavilySearch", Map.of(
                    "query", query,
                    "maxResults", 5,
                    "searchDepth", "advanced"
            ));
            McpSchema.CallToolResult result = client.callTool(req);
            return extractText(result);
        } catch (Exception e) {
            log.debug("MCP tavilySearch 调用失败: {}", e.getMessage());
            return "";
        }
    }

    private static boolean clientHasTool(McpSyncClient client, String toolName) {
        try {
            McpSchema.ListToolsResult toolsResult = client.listTools();
            if (toolsResult == null || toolsResult.tools() == null) {
                return false;
            }
            for (McpSchema.Tool t : toolsResult.tools()) {
                if (t != null && toolName.equalsIgnoreCase(t.name())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static String extractText(McpSchema.CallToolResult result) {
        if (result == null || result.content() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (McpSchema.Content c : result.content()) {
            if (c instanceof McpSchema.TextContent textContent) {
                if (StringUtils.hasText(textContent.text())) {
                    if (!sb.isEmpty()) {
                        sb.append("\n");
                    }
                    sb.append(textContent.text().trim());
                }
            }
        }
        return sb.toString().trim();
    }
}

