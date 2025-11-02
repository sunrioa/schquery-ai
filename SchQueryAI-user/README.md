# SchQueryAI User Module

## 功能介绍

本模块提供完整的用户登录注册功能，包括：

- 用户注册（用户名、密码、邮箱验证）
- 用户登录
- 邮箱验证码发送
- JWT令牌生成和验证
- 全局异常处理
- 参数校验

## API 接口

### 1. 发送注册验证码
- **接口地址**: `POST /user/sendRegisterCode`
- **请求参数**:
```json
{
  "email": "your-email@example.com"
}
```
- **响应示例**:
```json
{
  "code": "200",
  "msg": "验证码发送成功，请查收邮件",
  "data": "验证码发送成功"
}
```

### 2. 用户注册
- **接口地址**: `POST /user/register`
- **请求参数**:
```json
{
  "userName": "testuser",
  "password": "123456",
  "rePassword": "123456",
  "email": "your-email@example.com",
  "code": "123456"
}
```
- **响应示例**:
```json
{
  "code": "200",
  "msg": "注册成功",
  "data": {
    "id": 1,
    "userName": "testuser",
    "email": "your-email@example.com",
    "createTime": "2025-11-03T10:30:00",
    "updateTime": "2025-11-03T10:30:00"
  }
}
```

### 3. 用户登录
- **接口地址**: `POST /user/login`
- **请求参数**:
```json
{
  "userName": "testuser",
  "password": "123456"
}
```
- **响应示例**:
```json
{
  "code": "200",
  "msg": "登录成功",
  "data": {
    "id": 1,
    "userName": "testuser",
    "email": "your-email@example.com",
    "createTime": "2025-11-03T10:30:00",
    "updateTime": "2025-11-03T10:30:00"
  }
}
```

## 环境配置

### 1. 数据库配置
确保MySQL数据库已创建，数据库名为 `schqueryai`。

### 2. 邮件配置
修改 `application.yml` 中的邮件配置：
```yaml
spring:
  mail:
    host: smtp.qq.com
    port: 465
    username: your-email@qq.com
    password: your-smtp-authorization-code
    from: your-email@qq.com

mail-notify:
  host: smtp.qq.com
  port: 465
  username: your-email@qq.com
  password: your-smtp-authorization-code
  from: your-email@qq.com
```

### 3. Redis配置
确保Redis服务已启动，默认配置：
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: ""
```

### 4. 数据库表创建
执行以下SQL语句创建用户表：
```sql
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户主键ID',
    `user_name` VARCHAR(50) NOT NULL COMMENT '用户名',
    `pass_word` VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密存储）',
    `email` VARCHAR(100) NOT NULL COMMENT '电子邮箱',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_name` (`user_name`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';
```

## 安全特性

1. **密码加密**: 使用BCrypt对用户密码进行加密存储
2. **邮箱验证**: 注册时需要邮箱验证码验证
3. **参数校验**: 使用Spring Validation进行输入参数校验
4. **全局异常处理**: 统一处理各种异常情况
5. **跨域支持**: 配置CORS支持前端跨域访问

## 注意事项

1. 邮箱验证码有效期为5分钟
2. 用户名长度必须在6-20个字符之间
3. 密码长度必须在6-20个字符之间
4. 验证码为6位数字
5. 用户名和邮箱在系统中必须唯一

## 依赖说明

- Spring Boot Web
- Spring Boot Data Redis
- Spring Boot Mail
- MyBatis Plus
- MySQL Connector
- JWT (JSON Web Token)
- BCrypt 密码加密
- Lombok
- Spring Validation