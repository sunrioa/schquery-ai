create database SchQueryAI;

use SchQueryAI;


-- 创建用户表（系统用户，包含普通用户、客服、管理员等角色）
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户主键ID（唯一标识）',
    `user_name` VARCHAR(50) NOT NULL COMMENT '用户名（登录账号，唯一）',
    `pass_word` VARCHAR(255) NOT NULL COMMENT '密码（采用BCrypt加密存储）',
    `email` VARCHAR(100) NOT NULL COMMENT '电子邮箱（用于登录验证或通知，唯一）',
    `role` VARCHAR(20) NOT NULL COMMENT '用户角色（如：user-普通用户、worker-客服、admin-管理员）',
    `avatar` BIGINT COMMENT '头像',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_name` (`user_name`) COMMENT '用户名唯一索引',
    UNIQUE KEY `uk_email` (`email`) COMMENT '电子邮箱唯一索引'
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表（存储所有用户账号信息，含普通用户、客服和管理员）';

-- 会话表（用户的对话会话容器）
CREATE TABLE chat_session (
                              id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '会话主键ID（唯一标识）',
                              user_id BIGINT NOT NULL COMMENT '所属用户ID（关联user表id，标识会话归属）',
                              session_name VARCHAR(100) NOT NULL DEFAULT '新会话' COMMENT '会话名称（用户可自定义，默认"新会话"）',
                              status TINYINT NOT NULL DEFAULT 1 COMMENT '会话状态（1-活跃，0-已结束）',
                              last_message_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后一条消息的发送时间（用于会话排序）',
                              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '会话创建时间',
                              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '会话信息最后更新时间',
                              INDEX idx_user_id (user_id) COMMENT '用户ID索引（加速查询用户名下所有会话）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户与AI/客服的对话会话表（每个会话对应一个用户的连续对话）';


-- 对话消息表（存储会话中的具体消息内容）
CREATE TABLE chat_message (
                              id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '消息主键ID（唯一标识）',
                              session_id BIGINT NOT NULL COMMENT '所属会话ID（关联chat_session表id，标识消息归属的会话）',
                              message_type TINYINT NOT NULL COMMENT '消息发送者类型（0-用户发送，1-AI回复，-1-人工客服回复）',
                              content TEXT NOT NULL COMMENT '消息内容（文本格式，存储用户提问、AI回答或客服回复）',
                              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消息创建时间（发送时间）',
                              INDEX idx_session_id (session_id) COMMENT '会话ID索引（加速查询某会话下的所有消息）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话中的消息记录表（存储用户、AI、客服的所有对话内容）';

CREATE TABLE `image_store` (
                               `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                               `image_name` VARCHAR(255) DEFAULT NULL COMMENT '图片名称',
                               `image_base64` MEDIUMTEXT NOT NULL COMMENT 'base64编码的图片数据（建议包含格式前缀，如data:image/png;base64,...）',
                               `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
                               PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片/头像存储表（base64直接存储）';

CREATE TABLE `sensitive_words` (
                                   `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                   `word` varchar(255) NOT NULL COMMENT '敏感词内容（如“垃圾”“违规”）',
                                   `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-启用 0-禁用（临时下架）',
                                   PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=48594 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='敏感词词库';


CREATE TABLE `segmentation_words` (
                                      `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                      `word` varchar(255) NOT NULL COMMENT '敏感词',
                                      `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态（1-启用，0-禁用）',
                                      PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=48594 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分词词库';

-- 添加登录信息字段
ALTER TABLE `user` ADD COLUMN `last_login_ip` VARCHAR(50) NULL COMMENT '最后登录IP地址' AFTER `avatar`;
ALTER TABLE `user` ADD COLUMN `last_login_time` DATETIME NULL COMMENT '最后登录时间' AFTER `last_login_ip`;

-- 为登录IP字段添加索引，方便查询和统计
CREATE INDEX `idx_last_login_ip` ON `user`(`last_login_ip`);
CREATE INDEX `idx_last_login_time` ON `user`(`last_login_time`);

-- ============================================
-- 用户表地理位置字段
-- 创建时间: 2025-11-12
-- 描述: 为user表添加登录地理位置字段
-- ============================================

ALTER TABLE `user` ADD COLUMN `last_login_country` VARCHAR(50) NULL COMMENT '最后登录国家' AFTER `last_login_time`;
ALTER TABLE `user` ADD COLUMN `last_login_province` VARCHAR(50) NULL COMMENT '最后登录省份' AFTER `last_login_country`;
ALTER TABLE `user` ADD COLUMN `last_login_city` VARCHAR(50) NULL COMMENT '最后登录城市' AFTER `last_login_province`;

-- ============================================
-- 用户登录历史表
-- 创建时间: 2025-11-12
-- 描述: 记录用户每次登录的详细信息，用于安全审计和行为分析
-- ============================================

CREATE TABLE `login_history` (
                                 `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                 `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                 `user_name` VARCHAR(50) NOT NULL COMMENT '用户名',
                                 `login_ip` VARCHAR(50) NULL COMMENT '登录IP地址',
                                 `country` VARCHAR(50) NULL COMMENT '登录国家',
                                 `province` VARCHAR(50) NULL COMMENT '登录省份',
                                 `city` VARCHAR(50) NULL COMMENT '登录城市',
                                 `isp` VARCHAR(100) NULL COMMENT '运营商',
                                 `login_time` DATETIME NOT NULL COMMENT '登录时间',
                                 `status` INT NOT NULL DEFAULT 1 COMMENT '登录状态 (1-成功, 0-失败)',
                                 `fail_reason` VARCHAR(255) NULL COMMENT '失败原因',
                                 `user_agent` VARCHAR(500) NULL COMMENT '浏览器信息',
                                 `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 PRIMARY KEY (`id`),
                                 INDEX `idx_user_id` (`user_id`),
                                 INDEX `idx_login_time` (`login_time`),
                                 INDEX `idx_login_ip` (`login_ip`),
                                 INDEX `idx_city` (`city`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户登录历史记录表';

-- ============================================
-- 系统参数配置表（KV）
-- 用途：保存默认模型、默认知识库、MCP 配置等（对齐 rin-ai 的 chat.default.* 口径）
-- ============================================
CREATE TABLE IF NOT EXISTS `sys_config` (
                                           `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                           `config_key` VARCHAR(100) NOT NULL COMMENT '配置键',
                                           `config_name` VARCHAR(200) NULL COMMENT '配置名称',
                                           `config_value` TEXT NULL COMMENT '配置值',
                                           `config_type` CHAR(1) NULL DEFAULT 'N' COMMENT '系统内置（Y是 N否）',
                                           `remark` VARCHAR(500) NULL COMMENT '备注',
                                           `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-禁用',
                                           `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                           `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                           PRIMARY KEY (`id`),
                                           UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统参数配置（KV）';

-- ============================================
-- AI 模型配置表（模型管理）
-- ============================================
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

-- ============================================
-- 知识库表（知识库管理）
-- ============================================
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

-- 文档主表：存储完整文档的基本信息和状态
CREATE TABLE documents (
                           id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文档唯一标识ID',
                           knowledge_id BIGINT COMMENT '所属知识库ID，对应knowledge_info.id',
                           title VARCHAR(500) COMMENT '文档标题，默认为文件名',
                           content TEXT COMMENT '文档完整内容（小文件直接存储文本，大文件建议仅存关键摘要）',
                           file_path VARCHAR(500) COMMENT '文件存储路径（可选，如本地路径或云存储URL）',
                           file_type VARCHAR(50) COMMENT '文件类型（可选，如pdf、docx、txt等）',
                           upload_time DATETIME COMMENT '文档上传时间',
                           update_time DATETIME COMMENT '文档最后修改时间（内容或属性变更时更新）',
                           status TINYINT DEFAULT 1 COMMENT '文档状态：1-有效，0-删除（逻辑删除，避免物理删除数据）',
                           process_status TINYINT DEFAULT 0 COMMENT '处理状态：0-未处理，1-处理中，2-处理完成，3-处理失败（用于跟踪文档拆分、向量化流程）',
                           metadata JSON COMMENT '文档级扩展元数据（如作者、来源、权限标签等，按需动态存储）',
                           INDEX idx_knowledge_id (knowledge_id) COMMENT '按知识库筛选文档的索引',
                           INDEX idx_upload_time (upload_time) COMMENT '按上传时间查询的索引，加速时间范围筛选',
                           INDEX idx_status_process (status, process_status) COMMENT '按状态和处理状态联合查询的索引，优化筛选效率'
);

-- 文档片段表：存储文档拆分后的子片段，与向量数据库关联
CREATE TABLE document_chunks (
                                 id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '片段唯一标识ID',
                                 knowledge_id BIGINT COMMENT '所属知识库ID，对应knowledge_info.id',
                                 document_id BIGINT COMMENT '关联的文档ID，对应documents表的id',
                                 chunk_index INT COMMENT '片段在文档中的顺序编号（从0开始），用于重组完整文档',
                                 chunk_content TEXT COMMENT '片段具体内容（生成向量的原始文本）',
                                 chunk_length INT COMMENT '片段长度（用于粗略控制embedding输入大小）',
                                 created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '片段创建时间（拆分完成时自动记录）',
                                 qdrant_point_id VARCHAR(64) COMMENT '关联Qdrant向量数据库中该片段的point ID，用于向量检索后溯源',
                                 metadata JSON COMMENT '片段级扩展元数据（如页码、段落位置、关键词等）',
                                 INDEX idx_knowledge_id (knowledge_id) COMMENT '按知识库筛选片段的索引',
                                 INDEX idx_document_id (document_id) COMMENT '按文档ID查询其所有片段的索引，加速批量操作',
                                 UNIQUE KEY uk_qdrant_point_id (qdrant_point_id) COMMENT '确保Qdrant的point ID唯一，避免向量与片段多对一关联'
);
