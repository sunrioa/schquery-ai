create database SchQueryAI;

use SchQueryAI;


-- 创建用户表（系统用户，包含普通用户、客服、管理员等角色）
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户主键ID（唯一标识）',
    `user_name` VARCHAR(50) NOT NULL COMMENT '用户名（登录账号，唯一）',
    `pass_word` VARCHAR(255) NOT NULL COMMENT '密码（采用BCrypt加密存储）',
    `email` VARCHAR(100) NOT NULL COMMENT '电子邮箱（用于登录验证或通知，唯一）',
    `role` VARCHAR(20) NOT NULL COMMENT '用户角色（如：user-普通用户、worker-客服、admin-管理员）',
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