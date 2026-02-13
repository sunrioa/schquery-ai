# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

SchQueryAI 是一个基于 Spring Boot 3.4.3 + Vue 3 的智能问答系统，集成了通义千问大模型、知识库管理、RAG（检索增强生成）、语音识别等多种 AI 能力。

## 构建和运行

### 后端构建与运行

```bash
# 编译整个项目
mvn clean compile

# 打包（在项目根目录）
mvn clean package

# 运行应用（打包后在 SchQueryAI-application 模块下）
cd SchQueryAI-application
java -jar target/SchQueryAI-application-1.0.0.jar

# 或直接运行（需要指定 application 模块）
mvn spring-boot:run -pl SchQueryAI-application
```

### 前端构建与运行

```bash
cd SchQueryAI-front

# 安装依赖
npm install

# 开发环境运行（默认端口：8081）
npm run serve

# 生产环境构建
npm run build

# 代码检查
npm run lint
```

### 运行测试

```bash
# 在项目根目录运行所有测试
mvn test

# 运行单个测试类
mvn test -Dtest=TestApplication

# 运行单个测试方法
mvn test -Dtest=TestApplication#test
```

## 代码架构

### 模块划分（DDD 分层架构）

```
SchQueryAI/
├── SchQueryAI-domain/          # 领域层：通用领域模型、接口定义
├── SchQueryAI-utils/           # 工具模块：通用工具类
├── SchQueryAI-user/            # 用户模块：注册登录、聊天会话、客服系统
├── SchQueryAI-admin/           # 管理员模块：知识库管理、模型配置、系统监控
├── SchQueryAI-ai/              # AI 模块：对话服务、OCR、ASR、向量存储
├── SchQueryAI-application/     # 应用层：启动入口、全局配置
├── SchQueryAI-mcp-server/      # MCP 服务器：工具服务端点
└── SchQueryAI-front/           # 前端：Vue 3 + Element Plus
```

### 核心技术栈

**后端：**
- Spring Boot 3.4.3 + Java 17
- MyBatis-Plus 3.5.12（ORM）
- Redis（缓存、会话存储）
- MySQL 8.0（主数据库）
- Qdrant（向量数据库，用于 RAG）
- Spring AI 1.0.0（AI 集成框架）
- 通义千问 DashScope SDK（对话模型）
- Forest（HTTP 客户端，用于远程调用）
- WebSocket（实时通信）
- MinIO（文件存储）

**前端：**
- Vue 3 + Vite
- Element Plus（UI 组件库）
- Pinia（状态管理）
- Axios（HTTP 客户端）
- Marked + highlight.js（Markdown 渲染）

### AI 服务集成

**对话模型：**
- 模型：`qwen-plus`
- 嵌入模型：`text-embedding-v1`
- 向量维度：1536

**语音识别（ASR）：**
- 服务：通义 ASR
- 模型：`fun-asr-realtime-2025-11-07`
- 格式：PCM 16kHz
- 使用 WebSocket 实时传输

**OCR 服务：**
- 通过 `OcrRpc` 调用通义 OCR 接口

### 关键配置文件

- `SchQueryAI-application/src/main/resources/application.yml`：Spring Boot 主配置
- `SchQueryAI-front/package.json`：前端依赖和脚本
- `pom.xml`：Maven 父 POM，定义依赖版本

### RPC 服务（Forest HTTP 客户端）

位于 `SchQueryAI-ai/src/main/java/cn/ling/rpc/`：
- `IntentRecognizerRpc`：意图识别
- `RerankRpc`：重排序
- `SearchRpc`：搜索服务
- `OcrRpc`：OCR 识别

### 敏感词处理

- 敏感词存储在 `src/main/resources/sensitive_words`（Base64 编码）
- 相关测试类：`TestApplication.decodeSensitiveWordsToRawFile()`
- 服务：`SensitiveWordsService`、`SegmentationWordsService`

## 依赖说明

### Spring AI BOM

项目使用 Spring AI 1.0.0，需要添加 Spring 快照仓库：
```xml
<repository>
    <id>spring-snapshots</id>
    <url>https://repo.spring.io/snapshot</url>
</repository>
```

### 外部服务依赖

运行项目需要配置以下服务：
1. **MySQL 8.0**：主数据库
2. **Redis**：缓存和会话存储
3. **Qdrant**：向量数据库（RAG 检索）
4. **MinIO**：文件存储
5. **通义千问 API**：对话和嵌入模型
6. **SMTP 邮件服务**：发送验证码

## 开发注意事项

- Java 版本：17
- 启动类：`cn.ling.Application`
- 应用端口：8080（后端）、8081（前端开发服务器）
- WebSocket 端点配置在 `ChatController` 中
- 全局异常处理在 application 模块中配置
- CORS 配置在 application 模块中
