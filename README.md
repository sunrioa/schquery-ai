# SchQueryAI

SchQueryAI 是一个基于 Spring Boot、Spring AI 和 Vue 3 的招生咨询与知识库问答系统，包含用户端、管理端、RAG 检索、语音识别、MCP 搜索服务和前端页面。


## 项目结构

| 目录 | 说明 |
| --- | --- |
| `SchQueryAI-application` | 后端主启动模块与全局配置 |
| `SchQueryAI-ai` | AI 对话、RAG、Embedding、Rerank、ASR/OCR 等能力 |
| `SchQueryAI-user` | 用户、会话、登录、客服等业务能力 |
| `SchQueryAI-admin` | 管理端接口与系统配置能力 |
| `SchQueryAI-domain` | 公共领域对象与 DTO/VO |
| `SchQueryAI-utils` | JWT、文件解析、邮件、FFmpeg 等工具 |
| `SchQueryAI-mcp-server` | Tavily 搜索 MCP 服务 |
| `SchQueryAI-front` | Vue 3 + Element Plus 前端 |

## 环境要求

- JDK 17
- Maven 3.8+
- Node.js 18+ 和 npm
- MySQL 8.x
- Redis
- Qdrant
- MinIO（文件上传功能需要）
- FFmpeg（音频处理功能需要）

## 配置说明


最小必填变量：

| 变量 | 说明 |
| --- | --- |
| `DB_URL` | MySQL JDBC 连接 |
| `DB_USERNAME` / `DB_PASSWORD` | MySQL 账号和密码 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 连接 |
| `DASHSCOPE_API_KEY` | DashScope 兼容 OpenAI 接口、rerank、意图识别使用 |
| `QDRANT_HOST` / `QDRANT_PORT` / `QDRANT_API_KEY` | Qdrant 向量库连接 |
| `JWT_SECRET_KEY` | JWT 签名密钥，至少 32 字节 |

可选变量：

| 变量 | 说明 |
| --- | --- |
| `EMAIL_USERNAME` / `EMAIL_PASSWORD` / `EMAIL_FROM` | 邮件验证码发送 |
| `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | 文件存储 |
| `SEARCH_API_KEY` | 搜索增强接口 |
| `TAVILY_API_KEY` | MCP 搜索服务 |
| `FFMPEG_PATH` | FFmpeg 可执行文件路径 |

PowerShell 示例：

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/schqueryai?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your-db-password"
$env:DASHSCOPE_API_KEY="your-dashscope-api-key"
$env:JWT_SECRET_KEY="replace-with-a-random-secret-at-least-32-bytes"
```

## 数据库初始化

创建数据库后导入 SQL：

```sql
CREATE DATABASE schqueryai DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

然后按需执行：

- `SchQueryAI-application/src/main/resources/db/建表语句.sql`
- `SchQueryAI-application/src/main/resources/db/migration/*.sql`

## 启动后端

在项目根目录执行：

```bash
mvn -pl SchQueryAI-application -am spring-boot:run
```

默认服务端口为 `8080`，可通过 `SERVER_PORT` 修改。

## 启动前端

```bash
cd SchQueryAI-front
npm install
npm run serve
```

前端开发服务默认代理到 `http://localhost:8080`。

## 启动 MCP 服务

如果需要 Tavily 搜索 MCP 服务，先配置 `TAVILY_API_KEY`，然后执行：

```bash
mvn -pl SchQueryAI-mcp-server -am spring-boot:run
```

默认端口为 `8081`。

## 验证命令

后端主应用和 MCP 服务可用下面的命令做编译验证：

```bash
mvn -q -pl SchQueryAI-application,SchQueryAI-mcp-server -am compile -DskipTests
```

