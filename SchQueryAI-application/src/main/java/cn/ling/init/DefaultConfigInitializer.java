package cn.ling.init;

import cn.ling.service.KnowledgeInfoService;
import cn.ling.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 启动时补齐默认配置（避免首次使用/首次进入管理端时无默认知识库）
 */
@Slf4j
@Component
public class DefaultConfigInitializer implements ApplicationRunner {

    private static final String KEY_MCP_MODE = "chat.default.mcpMode";
    private static final String KEY_MCP_SERVERS = "chat.default.mcpServers";

    private final KnowledgeInfoService knowledgeInfoService;
    private final SysConfigService sysConfigService;

    public DefaultConfigInitializer(KnowledgeInfoService knowledgeInfoService, SysConfigService sysConfigService) {
        this.knowledgeInfoService = knowledgeInfoService;
        this.sysConfigService = sysConfigService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Long kid = knowledgeInfoService.ensureDefaultKnowledgeId();
            if (kid != null) {
                log.info("默认知识库已就绪，kid={}", kid);
            }
        } catch (Exception e) {
            log.warn("初始化默认知识库失败：{}", e.getMessage());
        }

        try {
            ensureConfigIfMissing(KEY_MCP_MODE, "MCP检索策略", "fallback", "MCP配置");
            // 对齐 rin-ai 默认 MCP Server（ruoyi-mcp-server / rin-mcp-server）
            ensureConfigIfMissing(KEY_MCP_SERVERS, "MCP服务URL列表", "http://127.0.0.1:8081", "MCP配置");
        } catch (Exception e) {
            log.warn("初始化默认MCP配置失败：{}", e.getMessage());
        }
    }

    private void ensureConfigIfMissing(String key, String name, String value, String remark) {
        String existing = sysConfigService.getConfigValue(key);
        if (StringUtils.hasText(existing)) {
            return;
        }
        sysConfigService.upsertConfig(key, value, name, remark);
    }
}
