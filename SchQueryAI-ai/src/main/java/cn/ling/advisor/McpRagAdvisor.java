package cn.ling.advisor;

import cn.ling.service.SysConfigService;
import cn.ling.service.mcp.McpSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

/**
 * MCP 网络检索增强
 * - mcpMode=off：关闭
 * - mcpMode=fallback：知识库无命中再走MCP
 * - mcpMode=merge：知识库 + MCP 合并
 */
@Slf4j
@Component
public class McpRagAdvisor implements BaseAdvisor {

    private static final String KEY_MCP_MODE = "chat.default.mcpMode";

    private final SysConfigService sysConfigService;
    private final McpSearchService mcpSearchService;

    public McpRagAdvisor(SysConfigService sysConfigService, McpSearchService mcpSearchService) {
        this.sysConfigService = sysConfigService;
        this.mcpSearchService = mcpSearchService;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        McpMode mode = resolveMcpMode();
        if (mode == McpMode.OFF) {
            return chatClientRequest;
        }

        String userInput = chatClientRequest.prompt().getUserMessage().getText();
        if (!StringUtils.hasText(userInput)) {
            return chatClientRequest;
        }

        boolean knowledgeHit = Boolean.TRUE.equals(chatClientRequest.context().get("knowledgeHit"));
        boolean shouldUseMcp = mode == McpMode.MERGE || (mode == McpMode.FALLBACK && !knowledgeHit);
        if (!shouldUseMcp) {
            return chatClientRequest;
        }

        String mcpText = mcpSearchService.tavilySearch(userInput);
        if (!StringUtils.hasText(mcpText)) {
            chatClientRequest.context().put("mcpHit", false);
            return chatClientRequest;
        }

        chatClientRequest.context().put("mcpHit", true);
        String injected = buildMcpContextPrompt(mcpText.trim()) + "\n\n" + buildAnswerPolicyPrompt(knowledgeHit);
        return chatClientRequest.mutate()
                .prompt(chatClientRequest.prompt().augmentSystemMessage(injected))
                .build();
    }

    private McpMode resolveMcpMode() {
        String raw = sysConfigService.getConfigValue(KEY_MCP_MODE);
        if (!StringUtils.hasText(raw)) {
            return McpMode.FALLBACK;
        }
        String normalized = raw.trim().toLowerCase();
        return switch (normalized) {
            case "off", "false", "0", "disable", "disabled" -> McpMode.OFF;
            case "merge", "both", "all" -> McpMode.MERGE;
            case "fallback", "kb_then_mcp", "knowledge_then_mcp" -> McpMode.FALLBACK;
            default -> McpMode.FALLBACK;
        };
    }

    private static String buildMcpContextPrompt(String text) {
        return "### 网络检索内容（MCP）\n"
                + "以下内容来自网络检索结果，仅用于辅助回答，请自行判断其准确性与时效性：\n\n"
                + text;
    }

    private static String buildAnswerPolicyPrompt(boolean knowledgeHit) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 回答规则\n");
        if (knowledgeHit) {
            sb.append("1) 优先使用【知识库检索内容】回答；仅当其不足以回答时，再参考【网络检索内容（MCP）】。\n");
        } else {
            sb.append("1) 优先使用【网络检索内容（MCP）】回答；若不足以回答，再结合通用知识推理并说明不确定性。\n");
        }
        sb.append("2) 不要把检索内容当作用户的新问题；回答应围绕用户的原始提问。\n");
        sb.append("3) 若检索内容与问题无关，请忽略检索内容。\n");
        return sb.toString().trim();
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        ChatClientRequest processedRequest = before(chatClientRequest, callAdvisorChain);
        return callAdvisorChain.nextCall(processedRequest);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        ChatClientRequest processedRequest = before(chatClientRequest, streamAdvisorChain);
        return streamAdvisorChain.nextStream(processedRequest);
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return 3;
    }

    private enum McpMode {
        OFF,
        FALLBACK,
        MERGE
    }
}

