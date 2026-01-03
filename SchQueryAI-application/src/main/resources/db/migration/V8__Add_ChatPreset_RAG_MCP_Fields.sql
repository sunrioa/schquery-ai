-- ============================================
-- V8: 为 chat_preset 添加 RAG 和 MCP 字段
-- - 新增字段：kid, mcp_mode, mcp_servers
-- 说明：
-- - 将 RAG 和 MCP 配置从全局移到每个预设中
-- - 兼容 MySQL 5.7+（使用 information_schema + PREPARE 做幂等）
-- ============================================

SET @db := DATABASE();

-- 1) 添加 kid 字段（知识库ID）
SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'chat_preset' AND COLUMN_NAME = 'kid'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE chat_preset ADD COLUMN kid VARCHAR(50) NULL COMMENT ''绑定知识库ID(knowledge_info.id)'' AFTER repetition_penalty',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 添加 mcp_mode 字段（MCP运行模式）
SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'chat_preset' AND COLUMN_NAME = 'mcp_mode'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE chat_preset ADD COLUMN mcp_mode VARCHAR(20) NULL DEFAULT ''fallback'' COMMENT ''MCP运行模式: off/fallback/merge'' AFTER kid',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) 添加 mcp_servers 字段（MCP服务器地址）
SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'chat_preset' AND COLUMN_NAME = 'mcp_servers'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE chat_preset ADD COLUMN mcp_servers TEXT NULL COMMENT ''MCP服务器地址（逗号分隔）'' AFTER mcp_mode',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4) 为 kid 创建索引
SET @idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'chat_preset' AND INDEX_NAME = 'idx_kid'
);
SET @sql := IF(@idx_exists = 0,
    'CREATE INDEX idx_kid ON chat_preset(kid)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
