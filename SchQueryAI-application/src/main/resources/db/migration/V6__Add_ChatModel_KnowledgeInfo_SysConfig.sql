-- ============================================
-- V6: 模型管理/知识库管理/默认配置（KV）迁移
-- - 新增：sys_config / chat_model / knowledge_info
-- - 调整：documents/document_chunks 增加 knowledge_id
-- - 调整：document_chunks token_count -> chunk_length（如存在旧字段）
-- - 回填：默认知识库 + 默认 chat.default.kid/kName + 历史数据 knowledge_id
-- 说明：
-- - 兼容 MySQL 5.7+（使用 information_schema + PREPARE 做幂等）
-- ============================================

-- 1) 新表：系统参数配置（KV）
CREATE TABLE IF NOT EXISTS `sys_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `config_key` VARCHAR(100) NOT NULL COMMENT '配置键',
    `config_name` VARCHAR(200) NULL COMMENT '配置名称',
    `config_value` TEXT NULL COMMENT '配置值',
    `remark` VARCHAR(500) NULL COMMENT '备注',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-禁用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统参数配置（KV）';

-- 2) 新表：AI 模型配置
CREATE TABLE IF NOT EXISTS `chat_model` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `category` VARCHAR(20) NOT NULL COMMENT '模型分类(chat/vector/rerank/image/...)',
    `model_name` VARCHAR(100) NOT NULL COMMENT '模型名称',
    `provider_name` VARCHAR(50) NULL COMMENT '供应商标识',
    `model_describe` VARCHAR(255) NULL COMMENT '模型描述',
    `model_price` DOUBLE NULL COMMENT '模型价格',
    `model_type` CHAR(1) NULL COMMENT '计费类型',
    `model_show` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用/显示(1-是,0-否)',
    `system_prompt` TEXT NULL COMMENT '系统提示词（仅 chat 类模型常用）',
    `api_host` VARCHAR(255) NULL COMMENT '请求地址',
    `api_key` VARCHAR(255) NULL COMMENT '密钥',
    `api_url` VARCHAR(50) NULL COMMENT '请求后缀',
    `priority` INT NOT NULL DEFAULT 1 COMMENT '优先级(越大越优先)',
    `remark` VARCHAR(500) NULL COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_category_model_name` (`category`, `model_name`),
    KEY `idx_category_show` (`category`, `model_show`),
    KEY `idx_model_name` (`model_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI模型配置';

-- 3) 新表：知识库
CREATE TABLE IF NOT EXISTS `knowledge_info` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '知识库主键ID',
    `kname` VARCHAR(100) NOT NULL COMMENT '知识库名称',
    `description` VARCHAR(1000) NULL COMMENT '描述',
    `system_prompt` TEXT NULL COMMENT '知识库系统提示词',
    `text_block_size` INT NULL DEFAULT 600 COMMENT '文本块大小',
    `overlap_char` INT NULL DEFAULT 100 COMMENT '重叠字符数',
    `retrieve_limit` INT NULL DEFAULT 6 COMMENT '检索返回条数(topK)',
    `use_rerank` TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用重排序(0-否 1-是)',
    `rerank_model_name` VARCHAR(100) NULL COMMENT '重排序模型名称(chat_model.model_name)',
    `candidate_count` INT NOT NULL DEFAULT 20 COMMENT '向量检索候选数量',
    `min_score` DOUBLE NULL COMMENT '重排序最低分数阈值',
    `vector_model_name` VARCHAR(50) NOT NULL DEFAULT 'qdrant' COMMENT '向量库类型(qdrant/...)',
    `embedding_model_name` VARCHAR(100) NULL COMMENT '向量模型名称(chat_model.model_name)',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-禁用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_kname` (`kname`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库';

-- 4) documents：增加 knowledge_id
SET @db := DATABASE();
SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'documents' AND COLUMN_NAME = 'knowledge_id'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE documents ADD COLUMN knowledge_id BIGINT NULL COMMENT ''所属知识库ID，对应knowledge_info.id'' AFTER id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'documents' AND INDEX_NAME = 'idx_knowledge_id'
);
SET @sql := IF(@idx_exists = 0,
    'CREATE INDEX idx_knowledge_id ON documents(knowledge_id)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5) document_chunks：增加 knowledge_id
SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'document_chunks' AND COLUMN_NAME = 'knowledge_id'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE document_chunks ADD COLUMN knowledge_id BIGINT NULL COMMENT ''所属知识库ID，对应knowledge_info.id'' AFTER id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'document_chunks' AND INDEX_NAME = 'idx_knowledge_id'
);
SET @sql := IF(@idx_exists = 0,
    'CREATE INDEX idx_knowledge_id ON document_chunks(knowledge_id)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 6) document_chunks：token_count -> chunk_length（如旧字段存在）
SET @token_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'document_chunks' AND COLUMN_NAME = 'token_count'
);
SET @chunk_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'document_chunks' AND COLUMN_NAME = 'chunk_length'
);

SET @sql := 'SELECT 1';
SET @sql := IF(@token_exists = 1 AND @chunk_exists = 0,
    'ALTER TABLE document_chunks CHANGE COLUMN token_count chunk_length INT NULL COMMENT ''片段长度（用于粗略控制embedding输入大小）''',
    @sql
);
SET @sql := IF(@token_exists = 0 AND @chunk_exists = 0,
    'ALTER TABLE document_chunks ADD COLUMN chunk_length INT NULL COMMENT ''片段长度（用于粗略控制embedding输入大小）'' AFTER chunk_content',
    @sql
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 7) 默认知识库：若 knowledge_info 为空则创建一条“默认知识库”
INSERT INTO `knowledge_info` (`kname`, `description`, `vector_model_name`, `retrieve_limit`, `candidate_count`, `use_rerank`, `status`)
SELECT '默认知识库', '系统自动创建的默认知识库', 'qdrant', 6, 20, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM `knowledge_info` LIMIT 1);

-- 8) 回填默认知识库配置（chat.default.kid/kName）& MCP 默认策略（不覆盖已有值）
SET @default_kid := (SELECT `id` FROM `knowledge_info` ORDER BY `id` ASC LIMIT 1);
SET @default_kname := (SELECT `kname` FROM `knowledge_info` WHERE `id` = @default_kid);

INSERT INTO `sys_config` (`config_key`, `config_name`, `config_value`, `remark`, `status`)
VALUES ('chat.default.kid', '默认知识库ID', CAST(@default_kid AS CHAR), '默认对话参数', 1)
ON DUPLICATE KEY UPDATE
    `config_name`  = IF(`config_name`  IS NULL OR `config_name`  = '', VALUES(`config_name`),  `config_name`),
    `config_value` = IF(`config_value` IS NULL OR `config_value` = '', VALUES(`config_value`), `config_value`),
    `remark`       = IF(`remark`       IS NULL OR `remark`       = '', VALUES(`remark`),       `remark`);

INSERT INTO `sys_config` (`config_key`, `config_name`, `config_value`, `remark`, `status`)
VALUES ('chat.default.kName', '默认知识库名称', @default_kname, '默认对话参数', 1)
ON DUPLICATE KEY UPDATE
    `config_name`  = IF(`config_name`  IS NULL OR `config_name`  = '', VALUES(`config_name`),  `config_name`),
    `config_value` = IF(`config_value` IS NULL OR `config_value` = '', VALUES(`config_value`), `config_value`),
    `remark`       = IF(`remark`       IS NULL OR `remark`       = '', VALUES(`remark`),       `remark`);

INSERT INTO `sys_config` (`config_key`, `config_name`, `config_value`, `remark`, `status`)
VALUES ('chat.default.mcpMode', 'MCP检索策略', 'fallback', 'MCP配置', 1)
ON DUPLICATE KEY UPDATE
    `config_name`  = IF(`config_name`  IS NULL OR `config_name`  = '', VALUES(`config_name`),  `config_name`),
    `config_value` = IF(`config_value` IS NULL OR `config_value` = '', VALUES(`config_value`), `config_value`),
    `remark`       = IF(`remark`       IS NULL OR `remark`       = '', VALUES(`remark`),       `remark`);

-- 9) 历史数据回填：documents/document_chunks knowledge_id
UPDATE `documents`
SET `knowledge_id` = @default_kid
WHERE `knowledge_id` IS NULL AND @default_kid IS NOT NULL;

UPDATE `document_chunks` c
JOIN `documents` d ON c.`document_id` = d.`id`
SET c.`knowledge_id` = d.`knowledge_id`
WHERE c.`knowledge_id` IS NULL AND d.`knowledge_id` IS NOT NULL;

-- 10) 修复 chunk_length（为空则按文本长度回填）
UPDATE `document_chunks`
SET `chunk_length` = CHAR_LENGTH(`chunk_content`)
WHERE (`chunk_length` IS NULL OR `chunk_length` = 0) AND `chunk_content` IS NOT NULL;

-- 11) 回填 DB 元数据（仅影响数据库字段，不影响已入库的向量 metadata）
UPDATE `documents`
SET `metadata` = JSON_SET(COALESCE(`metadata`, JSON_OBJECT()), '$.knowledge_id', `knowledge_id`)
WHERE `knowledge_id` IS NOT NULL
  AND (`metadata` IS NULL OR JSON_EXTRACT(`metadata`, '$.knowledge_id') IS NULL);

UPDATE `document_chunks`
SET `metadata` = JSON_SET(
    COALESCE(`metadata`, JSON_OBJECT()),
    '$.knowledge_id', `knowledge_id`,
    '$.document_id', `document_id`,
    '$.chunk_index', `chunk_index`
)
WHERE `knowledge_id` IS NOT NULL
  AND (`metadata` IS NULL
       OR JSON_EXTRACT(`metadata`, '$.knowledge_id') IS NULL
       OR JSON_EXTRACT(`metadata`, '$.document_id') IS NULL);

