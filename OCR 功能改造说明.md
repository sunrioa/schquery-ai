# OCR 功能改造完成说明

## 📋 实现概述

已成功将项目中的 PDF OCR 功能从本地 Python 服务切换为**阿里云通义千问 VL-OCR 远程模型**，实现了完整的远程 OCR 识别能力。

---

## ✅ 已完成的功能

### 1. **后端核心实现**

#### 1.1 JavaCV PDF 截图工具 ([PdfImageUtils.java](file://e:\bis\ruoyi\sch-query-ai\SchQueryAI-utils\src\main\java\cn\ling\utils\PdfImageUtils.java))
- ✅ 支持多页 PDF 转图片
- ✅ Base64 编码转换
- ✅ 可自定义 DPI 和图片格式
- ✅ 默认 150 DPI PNG 格式输出

#### 1.2 阿里云 VL-OCR 服务 ([AliyunVLOcrService](file://e:\bis\ruoyi\sch-query-ai\SchQueryAI-ai\src\main\java\cn\ling\service\impl\AliyunVLOcrServiceImpl.java))
- ✅ 基于 `chat_model` 表的 `image` 分类配置
- ✅ 使用 RestTemplate 调用 OpenAI 兼容接口
- ✅ 支持单图片和多图片批量识别
- ✅ 逐页识别并自动拼接结果
- ✅ 添加分页标记便于区分

#### 1.3 OCR 服务改造 ([OcrServiceImpl](file://e:\bis\ruoyi\sch-query-ai\SchQueryAI-ai\src\main\java\cn\ling\service\impl\OcrServiceImpl.java))
- ✅ 移除 Python OCR 服务健康检查
- ✅ 直接调用阿里云 VL-OCR 服务
- ✅ 完善的错误处理和异常提示

### 2. **前端管理界面**

#### 2.1 OCR 模型管理页面 ([OcrModel.vue](file://e:\bis\ruoyi\sch-query-ai\SchQueryAI-front\src\views\admin\ai\OcrModel.vue))
- ✅ 类似语音识别配置的专业界面
- ✅ 独立的 `image` 分类模型管理
- ✅ 支持增删改查操作
- ✅ 启用/停用状态控制
- ✅ 优先级设置

#### 2.2 路由和菜单
- ✅ 新增路由：`/admin/ai/ocr-model`
- ✅ 管理后台菜单：**AI 配置 → OCR 文字识别配置**

### 3. **依赖集成**

#### Maven 依赖更新
```xml
<!-- SchQueryAI-utils/pom.xml -->
<dependency>
    <groupId>org.bytedeco</groupId>
    <artifactId>javacv-platform</artifactId>
    <version>1.5.9</version>
</dependency>
```

---

## 🔧 配置步骤

### 第一步：数据库配置

在 `chat_model` 表中添加阿里云 VL-OCR 模型配置：

```sql
INSERT INTO chat_model (
    category, model_name, provider_name, model_describe, 
    api_host, api_key, priority, model_show
) VALUES (
    'image', 
    'qwen-vl-max-latest',  -- 或 qwen-vl-max、qwen-vl-plus 等
    'Aliyun', 
    '阿里云通义千问 VL-OCR 模型，支持高精度文字识别',
    'https://dashscope.aliyuncs.com/compatible-mode/v1',
    'sk-your-api-key-here',  -- 替换为您的 DashScope API Key
    9999,  -- 最高优先级
    1      -- 启用状态
);
```

### 第二步：前端配置

1. 登录管理后台
2. 进入 **AI 配置 → OCR 文字识别配置**
3. 点击"添加配置"
4. 填写以下信息：
   - **模型标识**: `qwen-vl-max-latest`
   - **服务商**: `Aliyun`
   - **API 地址**: `https://dashscope.aliyuncs.com/compatible-mode/v1`
   - **API Key**: 您的 DashScope API Key
   - **优先级**: `9999`（确保被优先选中）
   - **状态**: 启用

### 第三步：测试验证

上传一个 PDF 扫描件到知识库，系统会自动：
1. 检测 PDF 为扫描件（文本提取少）
2. 调用 JavaCV 将 PDF 转为图片
3. Base64 编码后发送给阿里云 VL 模型
4. 返回 OCR 识别结果并进行文本纠错
5. 完成向量化入库

---

## 📊 技术架构

```
PDF 上传
  ↓
[DocumentReaderStrategy] 尝试提取文本
  ↓ (内容少/为空 → 判定为扫描件)
[PdfImageUtils] JavaCV 渲染 PDF 为 BufferedImage
  ↓ 
[Base64 编码] 转换为 Base64 字符串
  ↓
[AliyunVLOcrService] 构建 OpenAI 兼容请求
  ├─ API Host: https://dashscope.aliyuncs.com/compatible-mode/v1
  ├─ Model: qwen-vl-max-latest
  └─ Content: [Text Prompt + Base64 Image]
  ↓
[阿里云 VL 模型] 多模态识别
  ↓
[文本后处理] 清理、格式化、分页标记
  ↓
[OCR 纠错] LLM 二次纠错（可选）
  ↓
返回识别文本 → 分块 → 向量化入库
```

---

## 🎯 核心代码流程

### PDF 识别主流程
```java
// OcrServiceImpl.doOcr()
public String doOcr(MultipartFile pdf_File) {
    // 调用阿里云 VL-OCR 服务
    String extractedText = aliyunVLOcrService.recognizePdf(pdf_File);
    return extractedText;
}

// AliyunVLOcrServiceImpl.recognizePdf()
public String recognizePdf(MultipartFile file) throws Exception {
    // 1. 获取 image 分类的最高优先级模型
    ChatModel chatModel = chatModelService.getHighestPriorityEnabledByCategory("image");
    
    // 2. PDF 转 Base64 图片
    List<String> base64Images = pdfImageUtils.convertPdfToBase64Images(pdfBytes);
    
    // 3. 批量识别所有页面
    return recognizeMultipleImages(base64Images, "image/png");
}

// AliyunVLOcrServiceImpl.recognizeWithModel()
public String recognizeWithModel(ChatModel chatModel, String base64Image, String mimeType) {
    // 构建 OpenAI 兼容请求
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("model", chatModel.getModelName());
    requestBody.put("messages", List.of(userMessage));
    
    // 发送 HTTP 请求
    ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, requestEntity, String.class);
    
    // 解析响应
    return content;
}
```

---

## ⚠️ 注意事项

### 1. API Key 安全
- ✅ 建议通过环境变量注入：`${ALIYUN_DASHSCOPE_API_KEY}`
- ✅ 生产环境不要硬编码在配置文件
- ✅ 定期更换 API Key

### 2. 费用控制
- 阿里云通义千问 VL 模型按 token 计费
- 建议在 `application.yml` 中添加配置：
  ```yaml
  ocr:
    max-pages-per-pdf: 10  # 单个 PDF 最多识别页数
    enable-cost-limit: true
    daily-cost-limit: 100  # 每日费用上限（元）
  ```

### 3. 性能优化
- 多页 PDF 会逐页识别，耗时较长
- 建议限制单个 PDF 的大小和页数
- 可考虑异步处理大文件

### 4. 错误处理
常见错误及解决方案：

| 错误信息 | 原因 | 解决方案 |
|---------|------|---------|
| OCR 模型未配置 | `chat_model` 表无 image 分类数据 | 在前端添加并启用模型 |
| API Key 无效 | Key 错误或过期 | 检查并更换正确的 Key |
| 请求超时 | 网络问题或图片过大 | 增加超时时间或压缩图片 |
| Token 超限 | 单页内容过多 | 降低 DPI 或裁剪图片 |

---

## 🚀 后续优化建议

### 短期优化
1. **添加缓存机制**
   - 对已识别的 PDF 进行缓存（MD5 哈希）
   - 避免重复识别相同文件

2. **图片压缩优化**
   - 过高的 DPI 会增加 token 消耗
   - 建议默认 150 DPI，最大 300 DPI

3. **并发控制**
   - 限制同时进行的 OCR 任务数
   - 避免 API 限流

### 长期优化
1. **多模型支持**
   - 支持切换不同厂商的 OCR 模型
   - 百度 OCR、腾讯 OCR、Google Vision 等

2. **智能策略**
   - 根据文档类型自动选择最优模型
   - 表格、公式、手写体等特殊处理

3. **成本分析**
   - 统计每日/每月 OCR 费用
   - 提供费用报表和预警

---

## 📝 测试清单

- [ ] 上传普通 PDF 文档（非扫描件）→ 应直接提取文本，不触发 OCR
- [ ] 上传扫描版 PDF → 应触发 OCR 识别
- [ ] 上传单页 PDF → 验证单页识别
- [ ] 上传多页 PDF（>10 页）→ 验证分页识别和拼接
- [ ] 上传模糊/低质量扫描件 → 验证识别准确率
- [ ] 上传包含表格的 PDF → 验证表格结构保持
- [ ] 上传包含公式的 PDF → 验证公式表达
- [ ] 断网情况上传 PDF → 验证错误提示
- [ ] API Key 错误 → 验证错误提示
- [ ] 超大 PDF（>50MB）→ 验证文件大小限制

---

## 📞 技术支持

- 阿里云通义千问 VL 文档：https://help.aliyun.com/zh/dashscope/
- 模型 API 地址：https://dashscope.aliyuncs.com/compatible-mode/v1
- JavaCV 文档：https://github.com/bytedeco/javacv

---

## ✅ 总结

本次改造成功实现了：
1. ✅ 移除对本地 Python OCR 服务的依赖
2. ✅ 采用阿里云通义千问 VL-OCR 远程模型
3. ✅ 基于 `chat_model` 表 `image` 分类配置
4. ✅ 完整的前端管理界面
5. ✅ 支持多页 PDF 自动识别和结果拼接

**即插即用**：只需在前端配置好 API Key，即可立即使用！
