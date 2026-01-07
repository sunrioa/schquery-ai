package cn.ling.mcpserve.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * MCP 工具集合
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolService {

    private final TavilyService tavilyService;

    @Tool(description = "获取一个指定前缀的随机数")
    public String add(@ToolParam(description = "字符前缀") String prefix) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyMMdd");
        String format = LocalDate.now().format(formatter);
        String replace = prefix + UUID.randomUUID().toString().replace("-", "");
        return format + replace;
    }

    @Tool(description = "获取当前时间")
    public LocalDateTime getCurrentTime() {
        return LocalDateTime.now();
    }

    @Tool(description = "使用 Tavily 进行网络搜索，获取最新的信息和答案")
    public String tavilySearch(
            @ToolParam(description = "搜索查询关键词") String query,
            @ToolParam(description = "最大结果数量，默认5条", required = false) Integer maxResults,
            @ToolParam(description = "搜索深度：basic（基础）或 advanced（高级），默认 advanced", required = false) String searchDepth) {
        try {
            log.info("[MCP] tavilySearch invoked: query={}, maxResults={}, searchDepth={}", query, maxResults, searchDepth);
            return tavilyService.formatResponse(tavilyService.search(query, maxResults, searchDepth));
        } catch (Exception e) {
            log.warn("[MCP] tavilySearch failed: {}", e.getMessage());
            return "搜索失败：" + e.getMessage();
        }
    }
}

