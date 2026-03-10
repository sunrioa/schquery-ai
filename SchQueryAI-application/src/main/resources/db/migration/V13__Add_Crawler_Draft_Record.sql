-- ============================================
-- V13: 新增爬虫草稿持久化表
-- ============================================

CREATE TABLE IF NOT EXISTS `crawler_draft_record` (
    `id` VARCHAR(64) NOT NULL COMMENT '草稿ID(UUID)',
    `url` VARCHAR(500) NOT NULL COMMENT '页面URL',
    `title` VARCHAR(255) NULL COMMENT '页面标题',
    `content` LONGTEXT NULL COMMENT '抓取内容',
    `content_length` INT NULL COMMENT '内容长度',
    `fetch_time` DATETIME NULL COMMENT '抓取时间',
    `updated_time` DATETIME NULL COMMENT '草稿更新时间',
    `edited` TINYINT NOT NULL DEFAULT 0 COMMENT '是否编辑：0-否，1-是',
    `saved` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已提交入库：0-否，1-是',
    `saved_time` DATETIME NULL COMMENT '提交入库时间',
    `knowledge_id` BIGINT NULL COMMENT '目标知识库ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_crawler_draft_url` (`url`),
    KEY `idx_crawler_draft_saved` (`saved`),
    KEY `idx_crawler_draft_fetch_time` (`fetch_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='爬虫抓取草稿记录';
