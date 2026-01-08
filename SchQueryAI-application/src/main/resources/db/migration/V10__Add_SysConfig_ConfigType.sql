-- ============================================
-- V10: sys_config 增加 config_type 字段
-- - 兼容 MySQL 5.7+（information_schema + PREPARE 幂等）
-- ============================================

SET @db := DATABASE();

SET @tbl_exists := (
    SELECT COUNT(1)
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_config'
);

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_config' AND COLUMN_NAME = 'config_type'
);

SET @sql := IF(@tbl_exists = 1 AND @col_exists = 0,
    'ALTER TABLE `sys_config` ADD COLUMN `config_type` CHAR(1) NULL DEFAULT ''N'' COMMENT ''系统内置（Y是 N否）'' AFTER `config_value`',
    'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

