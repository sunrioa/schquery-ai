package cn.ling.controller;

import cn.ling.Result;
import cn.ling.service.mcp.DynamicMcpClientService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 服务管理
 */
@RestController
@RequestMapping("/chat/mcp")
public class McpManagementController {

    @Resource
    private DynamicMcpClientService dynamicMcpClientService;

    @GetMapping("/servers")
    public Result<Map<String, Object>> getMcpServers() {
        List<String> urls = dynamicMcpClientService.getMcpServerUrls();
        Map<String, Object> data = new HashMap<>();
        data.put("servers", urls);
        return Result.success(data);
    }

    @PostMapping("/refresh")
    public Result<String> refresh() {
        dynamicMcpClientService.refreshMcpClients();
        return Result.success("MCP客户端已刷新");
    }

    @PostMapping("/clear")
    public Result<String> clear() {
        dynamicMcpClientService.clearCache();
        return Result.success("MCP客户端缓存已清除");
    }
}

