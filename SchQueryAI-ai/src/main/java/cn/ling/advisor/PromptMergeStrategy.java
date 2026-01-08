package cn.ling.advisor;

import lombok.Builder;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 提示词融合策略
 * 用于合理组织 用户提示词、知识库提示词、知识库内容、MCP内容
 */
public class PromptMergeStrategy {

    /**
     * 融合模式
     */
    public enum MergeMode {
        /** 用户提示词优先，知识库和MCP内容作为辅助 */
        USER_FIRST,

        /** 知识库优先，用户提示词作为补充 */
        KNOWLEDGE_FIRST,

        /** 平衡模式，按权重融合 */
        BALANCED,

        /** 分层模式，不同来源内容分层展示 */
        LAYERED
    }

    /**
     * 提示词组件
     */
    @Data
    @Builder
    public static class PromptComponent {
        /** 来源：user/knowledge/mcp */
        private String source;

        /** 内容 */
        private String content;

        /** 权重：0.0-1.0 */
        private Double weight;

        /** 优先级：数字越小越优先 */
        private Integer priority;

        /** 是否为核心提示（影响回答质量的关键提示） */
        private Boolean core;
    }

    /**
     * 融合配置
     */
    @Data
    @Builder
    public static class MergeConfig {
        /** 融合模式 */
        private MergeMode mode;

        /** 用户提示词权重 */
        @Builder.Default
        private Double userPromptWeight = 0.6;

        /** 知识库提示词权重 */
        @Builder.Default
        private Double knowledgePromptWeight = 0.25;

        /** MCP提示词权重 */
        @Builder.Default
        private Double mcpPromptWeight = 0.15;

        /** 最大总提示词长度 */
        @Builder.Default
        private Integer maxTotalLength = 8000;

        /** 是否保留用户原始提示词 */
        @Builder.Default
        private Boolean preserveUserPrompt = true;

        /** 是否在合并时添加分隔符 */
        @Builder.Default
        private Boolean addSeparators = true;
    }

    /**
     * 融合结果
     */
    @Data
    @Builder
    public static class MergeResult {
        /** 最终的系统提示词 */
        private String finalSystemPrompt;

        /** 使用的组件列表 */
        private List<PromptComponent> usedComponents;

        /** 调试信息 */
        private String debugInfo;
    }

    /**
     * 执行融合
     */
    public static MergeResult merge(
            String userPrompt,
            String knowledgePrompt,
            String knowledgeContent,
            String mcpContent,
            MergeConfig config) {

        if (config == null) {
            config = MergeConfig.builder().mode(MergeMode.USER_FIRST).build();
        }

        List<PromptComponent> components = new ArrayList<>();

        // 构建用户提示词组件
        if (StringUtils.hasText(userPrompt)) {
            components.add(PromptComponent.builder()
                    .source("user")
                    .content(userPrompt.trim())
                    .weight(config.getUserPromptWeight())
                    .priority(1)
                    .core(true)
                    .build());
        }

        // 构建知识库提示词组件
        if (StringUtils.hasText(knowledgePrompt)) {
            components.add(PromptComponent.builder()
                    .source("knowledge_prompt")
                    .content(knowledgePrompt.trim())
                    .weight(config.getKnowledgePromptWeight())
                    .priority(2)
                    .core(false)
                    .build());
        }

        // 构建知识库内容组件
        if (StringUtils.hasText(knowledgeContent)) {
            components.add(PromptComponent.builder()
                    .source("knowledge_content")
                    .content(knowledgeContent.trim())
                    .weight(config.getKnowledgePromptWeight())
                    .priority(3)
                    .core(false)
                    .build());
        }

        // 构建MCP内容组件
        if (StringUtils.hasText(mcpContent)) {
            components.add(PromptComponent.builder()
                    .source("mcp_content")
                    .content(mcpContent.trim())
                    .weight(config.getMcpPromptWeight())
                    .priority(4)
                    .core(false)
                    .build());
        }

        // 根据模式执行融合
        String finalPrompt = switch (config.getMode()) {
            case USER_FIRST -> mergeUserFirst(components, config);
            case KNOWLEDGE_FIRST -> mergeKnowledgeFirst(components, config);
            case BALANCED -> mergeBalanced(components, config);
            case LAYERED -> mergeLayered(components, config);
        };

        // 构建调试信息
        StringBuilder debugInfo = new StringBuilder();
        debugInfo.append("融合模式: ").append(config.getMode()).append("\n");
        debugInfo.append("组件数量: ").append(components.size()).append("\n");
        debugInfo.append("最终长度: ").append(finalPrompt.length()).append("\n");
        for (PromptComponent comp : components) {
            debugInfo.append("  - ").append(comp.getSource())
                    .append(" [权重:").append(comp.getWeight())
                    .append(", 优先级:").append(comp.getPriority())
                    .append(", 长度:").append(comp.getContent().length())
                    .append("]\n");
        }

        return MergeResult.builder()
                .finalSystemPrompt(finalPrompt)
                .usedComponents(components)
                .debugInfo(debugInfo.toString())
                .build();
    }

    /**
     * 用户提示词优先模式
     * 结构：[用户提示词] → [知识库/MCP作为参考资料]
     */
    private static String mergeUserFirst(List<PromptComponent> components, MergeConfig config) {
        StringBuilder sb = new StringBuilder();

        // 1. 用户核心提示词
        components.stream()
                .filter(c -> "user".equals(c.getSource()))
                .findFirst()
                .ifPresent(c -> {
                    sb.append("## 角色定位与核心任务\n");
                    sb.append(c.getContent()).append("\n\n");
                });

        // 2. 知识库内容作为参考
        boolean hasKnowledge = components.stream().anyMatch(c -> c.getSource().startsWith("knowledge"));
        if (hasKnowledge) {
            sb.append("## 参考资料（知识库）\n");
            sb.append("以下内容来自知识库检索，请在遵循上述角色定位的前提下参考使用：\n\n");

            components.stream()
                    .filter(c -> "knowledge_prompt".equals(c.getSource()))
                    .findFirst()
                    .ifPresent(c -> sb.append(c.getContent()).append("\n\n"));

            components.stream()
                    .filter(c -> "knowledge_content".equals(c.getSource()))
                    .findFirst()
                    .ifPresent(c -> sb.append(c.getContent()).append("\n\n"));
        }

        // 3. MCP内容作为补充
        components.stream()
                .filter(c -> "mcp_content".equals(c.getSource()))
                .findFirst()
                .ifPresent(c -> {
                    sb.append("## 补充资料（网络检索）\n");
                    sb.append("以下内容来自网络检索，仅作辅助参考：\n\n");
                    sb.append(c.getContent()).append("\n\n");
                });

        // 4. 回答要求
        sb.append("## 回答要求\n");
        sb.append("- 严格遵守【角色定位与核心任务】中定义的角色和规则\n");
        sb.append("- 优先使用【参考资料】中的知识，但表达方式应符合角色定位\n");
        sb.append("- 如果参考资料不足以回答，可以使用通用知识，但需说明\n");

        return truncateIfNeeded(sb.toString(), config.getMaxTotalLength());
    }

    /**
     * 知识库优先模式
     * 结构：[知识库提示词] → [知识库内容] → [用户提示词作为补充]
     */
    private static String mergeKnowledgeFirst(List<PromptComponent> components, MergeConfig config) {
        StringBuilder sb = new StringBuilder();

        // 1. 知识库提示词
        components.stream()
                .filter(c -> "knowledge_prompt".equals(c.getSource()))
                .findFirst()
                .ifPresent(c -> {
                    sb.append("## 核心指引（知识库）\n");
                    sb.append(c.getContent()).append("\n\n");
                });

        // 2. 知识库内容
        components.stream()
                .filter(c -> "knowledge_content".equals(c.getSource()))
                .findFirst()
                .ifPresent(c -> {
                    sb.append("## 知识库检索内容\n");
                    sb.append(c.getContent()).append("\n\n");
                });

        // 3. 用户提示词作为补充
        components.stream()
                .filter(c -> "user".equals(c.getSource()))
                .findFirst()
                .ifPresent(c -> {
                    sb.append("## 补充要求\n");
                    sb.append(c.getContent()).append("\n\n");
                });

        // 4. MCP内容
        components.stream()
                .filter(c -> "mcp_content".equals(c.getSource()))
                .findFirst()
                .ifPresent(c -> {
                    sb.append("## 网络检索补充\n");
                    sb.append(c.getContent()).append("\n\n");
                });

        sb.append("## 回答策略\n");
        sb.append("- 严格基于【知识库检索内容】回答\n");
        sb.append("- 在满足知识库要求的基础上，兼顾【补充要求】\n");

        return truncateIfNeeded(sb.toString(), config.getMaxTotalLength());
    }

    /**
     * 平衡模式
     * 根据权重动态分配各部分的篇幅
     */
    private static String mergeBalanced(List<PromptComponent> components, MergeConfig config) {
        StringBuilder sb = new StringBuilder();

        // 计算总权重
        double totalWeight = components.stream()
                .mapToDouble(c -> c.getWeight() != null ? c.getWeight() : 0.0)
                .sum();

        if (totalWeight == 0) {
            totalWeight = 1.0;
        }

        // 按权重分配长度
        int maxLength = config.getMaxTotalLength();
        for (PromptComponent comp : components) {
            double ratio = comp.getWeight() / totalWeight;
            int allowedLength = (int) (maxLength * ratio);
            String truncated = truncateIfNeeded(comp.getContent(), allowedLength);

            sb.append("### ").append(getSourceLabel(comp.getSource()))
                    .append(" (权重: ").append(String.format("%.0f%%", ratio * 100)).append(")\n");
            sb.append(truncated).append("\n\n");
        }

        return sb.toString();
    }

    /**
     * 分层模式
     * 清晰分层，互不覆盖
     */
    private static String mergeLayered(List<PromptComponent> components, MergeConfig config) {
        StringBuilder sb = new StringBuilder();

        sb.append("# AI助手配置（多层提示词融合）\n\n");

        // 按优先级排序
        components.stream()
                .sorted((a, b) -> Integer.compare(
                        a.getPriority() != null ? a.getPriority() : 999,
                        b.getPriority() != null ? b.getPriority() : 999
                ))
                .forEach(comp -> {
                    sb.append("---\n");
                    sb.append("## ").append(getSourceLabel(comp.getSource())).append("\n");
                    if (comp.getCore() != null && comp.getCore()) {
                        sb.append("**[核心层级]**\n");
                    }
                    sb.append(comp.getContent()).append("\n\n");
                });

        sb.append("---\n");
        sb.append("## 融合规则\n");
        sb.append("- 所有层级的提示词都需要遵守\n");
        sb.append("- 当出现冲突时，核心层级优先\n");
        sb.append("- 各层级内容应相互补充，而非相互替代\n");

        return truncateIfNeeded(sb.toString(), config.getMaxTotalLength());
    }

    private static String getSourceLabel(String source) {
        return switch (source) {
            case "user" -> "用户角色定位";
            case "knowledge_prompt" -> "知识库提示";
            case "knowledge_content" -> "知识库内容";
            case "mcp_content" -> "网络检索内容";
            default -> source;
        };
    }

    private static String truncateIfNeeded(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (maxLength <= 0) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        if (maxLength <= 20) {
            return text.substring(0, maxLength);
        }
        return text.substring(0, maxLength - 20) + "\n\n[内容过长已截断...]";
    }
}
