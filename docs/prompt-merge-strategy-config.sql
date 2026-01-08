-- ============================================
-- 提示词融合策略配置 SQL
-- 用于配置 MCP、知识库、用户提示词的优先级和权重
-- ============================================

-- 1. 添加系统配置：提示词融合模式
-- 说明：控制用户提示词、知识库内容、MCP内容的融合方式
-- 可选值：
--   - user_first: 用户提示词优先（推荐）
--   - knowledge_first: 知识库优先
--   - balanced: 平衡模式，按权重分配
--   - layered: 分层模式，清晰分层
INSERT INTO sys_config (config_name, config_key, config_value, config_type, remark, create_time, update_time)
VALUES
('提示词融合模式', 'chat.default.promptMergeMode', 'user_first', 'Y',
'控制用户提示词、知识库、MCP内容的融合方式。可选：user_first(用户优先)/knowledge_first(知识库优先)/balanced(平衡)/layered(分层)',
NOW(), NOW())
ON DUPLICATE KEY UPDATE
config_value = 'user_first',
remark = '控制用户提示词、知识库、MCP内容的融合方式。可选：user_first(用户优先)/knowledge_first(知识库优先)/balanced(平衡)/layered(分层)',
update_time = NOW();

-- 2. 添加系统配置：MCP 模式（如果尚未添加）
INSERT INTO sys_config (config_name, config_key, config_value, config_type, remark, create_time, update_time)
VALUES
('MCP运行模式', 'chat.default.mcpMode', 'fallback', 'Y',
'MCP网络检索模式。可选：off(关闭)/fallback(知识库无结果时使用)/merge(与知识库合并)',
NOW(), NOW())
ON DUPLICATE KEY UPDATE
config_value = 'fallback',
remark = 'MCP网络检索模式。可选：off(关闭)/fallback(知识库无结果时使用)/merge(与知识库合并)',
update_time = NOW();

-- 3. 添加权重配置（可选，未来扩展用）
INSERT INTO sys_config (config_name, config_key, config_value, config_type, remark, create_time, update_time)
VALUES
('用户提示词权重', 'chat.weight.user', '0.6', 'Y',
'用户提示词在融合时的权重（0.0-1.0）',
NOW(), NOW())
ON DUPLICATE KEY UPDATE
config_value = '0.6',
update_time = NOW();

INSERT INTO sys_config (config_name, config_key, config_value, config_type, remark, create_time, update_time)
VALUES
('知识库权重', 'chat.weight.knowledge', '0.25', 'Y',
'知识库内容在融合时的权重（0.0-1.0）',
NOW(), NOW())
ON DUPLICATE KEY UPDATE
config_value = '0.25',
update_time = NOW();

INSERT INTO sys_config (config_name, config_key, config_value, config_type, remark, create_time, update_time)
VALUES
('MCP权重', 'chat.weight.mcp', '0.15', 'Y',
'MCP网络检索内容在融合时的权重（0.0-1.0）',
NOW(), NOW())
ON DUPLICATE KEY UPDATE
config_value = '0.15',
update_time = NOW();

-- ============================================
-- 使用说明
-- ============================================
/*
1. 提示词融合模式详解：

   a) user_first（用户优先）- 推荐默认模式
      结构：[用户提示词] → [知识库作为参考] → [MCP作为补充]
      适用场景：
        - 用户需要定制化的AI角色（如客服、助手等）
        - 用户提示词包含重要的行为规则和约束
        - 知识库和MCP仅作为信息来源补充
      示例：
        你是一个专业的医疗顾问。【用户定义】
        参考资料（知识库）：[疾病信息...]
        补充资料（网络）：[最新研究...]

   b) knowledge_first（知识库优先）
      结构：[知识库提示词] → [知识库内容] → [用户提示词作为补充]
      适用场景：
        - 强制基于知识库回答，不允许发散
        - 专业领域问答（法律、医疗等）
        - 用户提示词仅用于微调回答风格
      示例：
        严格基于知识库医疗文档回答。【知识库定义】
        知识库内容：[专业医疗信息...]
        补充要求：请用通俗语言解释。【用户补充】

   c) balanced（平衡模式）
      结构：根据权重动态分配各部分的篇幅
      适用场景：
        - 需要灵活权衡各方信息
        - 用于实验和调优
        - 各信息源同等重要
      权重示例：
        - 用户提示词：60%
        - 知识库内容：25%
        - MCP内容：15%

   d) layered（分层模式）
      结构：清晰分层，互不覆盖
      适用场景：
        - 调试和分析
        - 需要明确区分各层级提示词
        - 复杂场景需要精确控制
      示例：
        ## 层级1：用户角色定位 [核心]
        ## 层级2：知识库提示
        ## 层级3：知识库内容
        ## 层级4：网络检索内容

2. MCP模式详解：

   a) off：完全关闭MCP网络检索

   b) fallback（推荐）：
      - 知识库有结果 → 仅使用知识库
      - 知识库无结果 → 启用MCP网络检索

   c) merge：
      - 同时使用知识库和MCP
      - 内容会根据融合模式组织

3. 配置调整建议：

   场景1：客服机器人
   - promptMergeMode: user_first
   - mcpMode: fallback
   - 原因：用户定义的客服规则最重要，知识库提供产品信息

   场景2：专业问答系统（法律、医疗）
   - promptMergeMode: knowledge_first
   - mcpMode: off
   - 原因：必须严格基于专业知识库，不允许网络信息干扰

   场景3：通用AI助手
   - promptMergeMode: balanced
   - mcpMode: merge
   - 原因：需要综合多方信息，灵活回答

   场景4：实验和调试
   - promptMergeMode: layered
   - mcpMode: merge
   - 原因：清晰看到各层级提示词效果

4. 调试和监控：

   查看融合日志：
   - 日志级别设置为 DEBUG
   - 查看 McpRagAdvisor 的日志输出
   - 会显示融合详情：模式、权重、长度等

5. 未来扩展：

   在 chat_preset 表中添加字段（可选）：

   ALTER TABLE chat_preset ADD COLUMN prompt_merge_mode VARCHAR(50)
   COMMENT '提示词融合模式：user_first/knowledge_first/balanced/layered';

   这样每个预设可以有独立的融合策略。
*/

-- 查询当前配置
SELECT
    config_name AS '配置名称',
    config_key AS '配置键',
    config_value AS '当前值',
    remark AS '说明'
FROM sys_config
WHERE config_key LIKE 'chat.default.%' OR config_key LIKE 'chat.weight.%'
ORDER BY config_key;
