-- ============================================
-- V9: 为 customer_service_message 添加 sender_avatar 字段
-- - 新增字段：sender_avatar
-- 说明：
-- - 存储发送者头像URL，用于前端显示
-- - 兼容 MySQL 5.7+（使用 information_schema + PREPARE 做幂等）
-- ============================================

SET @db := DATABASE();

-- 1) 添加 sender_avatar 字段（发送者头像）
SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'customer_service_message' AND COLUMN_NAME = 'sender_avatar'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE customer_service_message ADD COLUMN sender_avatar VARCHAR(500) NULL COMMENT ''发送者头像URL'' AFTER sender_name',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
