# SchQueryAI

SchQueryAI 是一个基于 Spring Boot、Spring AI 和 Vue 3 的招生咨询与知识库问答系统，包含用户端、管理端、RAG 检索、语音识别、MCP 搜索服务和前端页面。

GitHub 仓库地址：`https://github.com/sunrioa/schquery-ai.git`

## 开源状态

本仓库已完成开源前脱敏处理：

- 运行配置中的数据库连接、Redis 密码、邮箱授权码、AI 服务 Key、Tavily Key、MinIO 密钥、JWT 密钥均改为环境变量。
- Git 历史已移除本地 IDE/Agent 配置、个人文档、旧私密脚本和已知敏感值。
- `.env`、本地覆盖配置、Office 临时文件、Agent 本地目录已加入 `.gitignore`。

如果你曾经在本地或远端提交过真实密钥，仍然需要到对应服务商控制台吊销并重新生成。Git 历史清理不能让已经泄露过的密钥重新变安全。

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

项目已移除真实数据库连接、公网 IP、邮箱授权码和 API Key。所有敏感信息都通过环境变量注入，参考 `.env.example`。

不要把 `.env` 或本地 `application-*.local.yml` 提交到仓库。公开仓库只保留 `.env.example` 作为配置模板。

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

## 开源前安全检查

- 不要提交 `.env`、`application-local.yml`、`application-*.local.yml` 等本地私密配置。
- 不要提交 `.agents/`、`.claude/`、`.idea/`、`.vscode/settings.json` 等个人工具配置。
- 如果真实密钥曾经提交到 Git 历史，请立即在服务商控制台吊销并重新生成。
- 如果仓库已经推送到远程，需要清理历史后使用 `git push --force-with-lease` 更新远端。
- 建议开启 GitHub Secret scanning 和 Push protection。
