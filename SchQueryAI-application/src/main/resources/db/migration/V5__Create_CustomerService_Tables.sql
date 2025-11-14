-- 创建客服消息表
CREATE TABLE IF NOT EXISTS `customer_service_message` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `user_name` VARCHAR(50) COMMENT '用户名',
  `message_content` LONGTEXT NOT NULL COMMENT '消息内容',
  `sender_type` INT DEFAULT 1 COMMENT '发送者类型 (1-用户, 2-管理员)',
  `sender_id` BIGINT COMMENT '发送者ID',
  `sender_name` VARCHAR(50) COMMENT '发送者名称',
  `read_status` INT DEFAULT 0 COMMENT '消息状态 (1-已读, 0-未读)',
  `topic` VARCHAR(100) COMMENT '话题/类别',
  `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY `idx_user_id` (`user_id`) COMMENT '用户ID索引',
  KEY `idx_sender_id` (`sender_id`) COMMENT '发送者ID索引',
  KEY `idx_create_time` (`create_time`) COMMENT '创建时间索引',
  KEY `idx_read_status` (`read_status`) COMMENT '读状态索引',
  CONSTRAINT `fk_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服消息表';

-- 创建客服会话汇总表（用于快速查询待处理的客服请求）
CREATE TABLE IF NOT EXISTS `customer_service_session` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `user_id` BIGINT NOT NULL UNIQUE COMMENT '用户ID',
  `user_name` VARCHAR(50) COMMENT '用户名',
  `last_message` LONGTEXT COMMENT '最后一条消息',
  `unread_count` INT DEFAULT 0 COMMENT '未读消息数',
  `session_status` INT DEFAULT 0 COMMENT '会话状态 (0-待处理, 1-处理中, 2-已完成)',
  `assigned_admin_id` BIGINT COMMENT '分配给的管理员ID',
  `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY `idx_session_status` (`session_status`) COMMENT '会话状态索引',
  KEY `idx_assigned_admin_id` (`assigned_admin_id`) COMMENT '分配管理员ID索引',
  KEY `idx_create_time` (`create_time`) COMMENT '创建时间索引',
  CONSTRAINT `fk_user_id_session` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服会话汇总表';
