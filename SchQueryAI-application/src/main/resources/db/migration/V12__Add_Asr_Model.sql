-- ============================================
-- V12: 新增 ASR 模型配置表
-- ============================================

CREATE TABLE IF NOT EXISTS `asr_model` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `model_name` VARCHAR(100) NOT NULL COMMENT '模型名称',
    `provider_name` VARCHAR(50) NULL COMMENT '供应商标识',
    `model_describe` VARCHAR(255) NULL COMMENT '模型描述',
    `model_show` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用/显示(1-是,0-否)',
    `endpoint` VARCHAR(255) NULL COMMENT 'WebSocket地址',
    `api_key` VARCHAR(255) NULL COMMENT '密钥',
    `auth_header_name` VARCHAR(100) NULL COMMENT '鉴权Header名称',
    `auth_header_value` VARCHAR(255) NULL COMMENT '鉴权Header值',
    `subprotocol` VARCHAR(100) NULL COMMENT 'WebSocket子协议',
    `format` VARCHAR(50) NOT NULL DEFAULT 'pcm' COMMENT '音频格式',
    `sample_rate` INT NOT NULL DEFAULT 16000 COMMENT '采样率',
    `enable_intermediate_result` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用中间结果',
    `enable_punctuation` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用标点',
    `enable_inverse_text_normalization` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用逆文本归一化',
    `hot_words` TEXT NULL COMMENT '热词配置(JSON)',
    `use_header_payload` TINYINT NOT NULL DEFAULT 1 COMMENT '是否使用header/payload结构',
    `chunk_bytes` INT NOT NULL DEFAULT 960 COMMENT '音频分片字节数',
    `chunk_interval_ms` INT NOT NULL DEFAULT 10 COMMENT '分片发送间隔(毫秒)',
    `connect_timeout_ms` INT NOT NULL DEFAULT 10000 COMMENT '连接超时(毫秒)',
    `priority` INT NOT NULL DEFAULT 1 COMMENT '优先级(越大越优先)',
    `remark` VARCHAR(500) NULL COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_asr_model_name` (`model_name`),
    KEY `idx_model_show` (`model_show`),
    KEY `idx_priority` (`priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ASR模型配置';
