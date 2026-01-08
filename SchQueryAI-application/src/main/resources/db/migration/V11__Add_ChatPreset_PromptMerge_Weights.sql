-- ============================================
-- V11: chat_preset 增加提示词融合策略字段
-- - 新增字段：prompt_merge_mode, weight_user, weight_knowledge, weight_mcp
-- - 兼容 MySQL 5.7+（information_schema + PREPARE 幂等）
-- ============================================

SET @db := DATABASE();

SET @tbl_exists := (
    SELECT COUNT(1)
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'chat_preset'
);

-- 1) prompt_merge_mode
SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'chat_preset' AND COLUMN_NAME = 'prompt_merge_mode'
);
SET @sql := IF(@tbl_exists = 1 AND @col_exists = 0,
    'ALTER TABLE chat_preset ADD COLUMN prompt_merge_mode VARCHAR(50) NULL COMMENT ''提示词融合模式：user_first/knowledge_first/balanced/layered'' AFTER mcp_servers',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) weight_user
SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'chat_preset' AND COLUMN_NAME = 'weight_user'
);
SET @sql := IF(@tbl_exists = 1 AND @col_exists = 0,
    'ALTER TABLE chat_preset ADD COLUMN weight_user DOUBLE NULL COMMENT ''用户提示词权重（0-1）'' AFTER prompt_merge_mode',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) weight_knowledge
SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'chat_preset' AND COLUMN_NAME = 'weight_knowledge'
);
SET @sql := IF(@tbl_exists = 1 AND @col_exists = 0,
    'ALTER TABLE chat_preset ADD COLUMN weight_knowledge DOUBLE NULL COMMENT ''知识库权重（0-1）'' AFTER weight_user',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4) weight_mcp
SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'chat_preset' AND COLUMN_NAME = 'weight_mcp'
);
SET @sql := IF(@tbl_exists = 1 AND @col_exists = 0,
    'ALTER TABLE chat_preset ADD COLUMN weight_mcp DOUBLE NULL COMMENT ''MCP权重（0-1）'' AFTER weight_knowledge',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

