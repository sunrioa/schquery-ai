-- ============================================
-- V7: 对话预设（角色/参数预设）
-- - 新增：chat_preset（多套模型参数预设）
-- - 调整：chat_session 增加 preset_id（会话绑定预设）
-- 说明：
-- - 兼容 MySQL 5.7+（使用 information_schema + PREPARE 做幂等）
-- ============================================

-- 1) 新表：对话预设（角色/参数预设）
CREATE TABLE IF NOT EXISTS `chat_preset` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `preset_name` VARCHAR(100) NOT NULL COMMENT '预设名称（角色）',
    `model` VARCHAR(100) NULL COMMENT '模型名称（chat_model.model_name）',
    `talk_count` INT NULL COMMENT '上下文轮数（预留）',
    `max_tokens` INT NULL COMMENT '最大输出token',
    `system_message` TEXT NULL COMMENT '系统提示词（角色设定）',
    `temperature` DOUBLE NULL COMMENT 'temperature',
    `top_p` DOUBLE NULL COMMENT 'top_p',
    `presence_penalty` DOUBLE NULL COMMENT 'presence_penalty',
    `frequency_penalty` DOUBLE NULL COMMENT 'frequency_penalty',
    `repetition_penalty` DOUBLE NULL COMMENT 'repetition_penalty（预留）',
    `remark` VARCHAR(500) NULL COMMENT '备注',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-禁用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_preset_name` (`preset_name`),
    KEY `idx_model` (`model`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话预设（角色/参数预设）';

-- 2) chat_session：增加 preset_id（如表存在且字段不存在）
SET @db := DATABASE();
SET @tbl_exists := (
    SELECT COUNT(1)
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'chat_session'
);
SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'chat_session' AND COLUMN_NAME = 'preset_id'
);
SET @sql := IF(@tbl_exists = 1 AND @col_exists = 0,
    'ALTER TABLE chat_session ADD COLUMN preset_id BIGINT NULL COMMENT ''对话预设ID(chat_preset.id)'' AFTER user_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) preset_id 索引（如字段存在且索引不存在）
SET @idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'chat_session' AND INDEX_NAME = 'idx_preset_id'
);
SET @sql := IF(@tbl_exists = 1 AND @col_exists = 1 AND @idx_exists = 0,
    'CREATE INDEX idx_preset_id ON chat_session(preset_id)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

