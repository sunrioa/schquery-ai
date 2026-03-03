package cn.ling.service.impl;

import cn.ling.domain.pojo.ChatModel;
import cn.ling.service.AliyunVLOcrService;
import cn.ling.service.ChatModelService;
import cn.ling.utils.PdfImageUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 阿里云通义千问 VL-OCR 服务实现类
 * 基于 RestTemplate 和阿里云 DashScope API 实现多模态 OCR 识别
 * 使用 OpenAI 兼容格式调用通义千问 VL 模型
 */
@Slf4j
@Service
public class AliyunVLOcrServiceImpl implements AliyunVLOcrService {

    @Resource
    private ChatModelService chatModelService;

    @Resource
    private PdfImageUtils pdfImageUtils;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * OCR 识别的系统提示词
     */
    private static final String OCR_SYSTEM_PROMPT = """
            你是一个专业的 OCR（光学字符识别）助手。请仔细分析图片中的所有文字内容，并完整、准确地提取出来。
            
            要求：
            1. 保持原文的段落结构和格式
            2. 不要遗漏任何文字
            3. 不要添加任何解释或总结
            4. 如果是表格，尽量保持行列结构
            5. 如果有公式或特殊符号，用文本方式近似表达
            
            请直接输出识别结果，不要有任何前缀说明。
            """;

    @Override
    public String recognizePdf(MultipartFile file) throws Exception {
        log.info("开始对 PDF 文件进行 OCR 识别，文件名：{}, 大小：{} bytes", 
                file.getOriginalFilename(), file.getSize());

        // 1. 获取 image 分类的最高优先级模型
        ChatModel chatModel = chatModelService.getHighestPriorityEnabledByCategory("image");
        if (chatModel == null) {
            log.error("未找到启用的 image 分类模型，无法进行 OCR 识别");
            throw new IllegalStateException("请先在模型管理中配置并启用 image 分类的 OCR 模型");
        }
        
        log.info("使用 OCR 模型：{}, API 地址：{}", chatModel.getModelName(), chatModel.getApiHost());

        // 2. 将 PDF 转换为 Base64 图片列表
        byte[] pdfBytes = file.getBytes();
        List<String> base64Images = pdfImageUtils.convertPdfToBase64Images(pdfBytes);
        
        if (base64Images.isEmpty()) {
            log.error("PDF 转换失败，没有生成任何图片");
            throw new IOException("PDF 转换失败，没有生成任何图片");
        }
        
        log.info("PDF 转换成功，共 {} 张图片", base64Images.size());

        // 3. 调用多图片识别方法
        return recognizeMultipleImages(base64Images, "image/png");
    }

    @Override
    public String recognizeBase64Image(String base64Image, String mimeType) throws Exception {
        ChatModel chatModel = chatModelService.getHighestPriorityEnabledByCategory("image");
        if (chatModel == null) {
            throw new IllegalStateException("未找到启用的 image 分类模型");
        }
        
        return recognizeWithModel(chatModel, base64Image, mimeType);
    }

    @Override
    public String recognizeMultipleImages(List<String> base64Images, String mimeType) throws Exception {
        log.info("开始批量识别 {} 张图片", base64Images.size());
        
        if (base64Images == null || base64Images.isEmpty()) {
            throw new IllegalArgumentException("图片列表为空");
        }

        ChatModel chatModel = chatModelService.getHighestPriorityEnabledByCategory("image");
        if (chatModel == null) {
            throw new IllegalStateException("未找到启用的 image 分类模型");
        }

        StringBuilder allText = new StringBuilder();
        
        // 逐页识别
        for (int i = 0; i < base64Images.size(); i++) {
            log.debug("正在识别第 {}/{} 页", i + 1, base64Images.size());
            
            String pageText = recognizeWithModel(chatModel, base64Images.get(i), mimeType);
            
            if (StringUtils.hasText(pageText)) {
                allText.append(pageText);
                
                // 如果不是最后一页，添加分页标记
                if (i < base64Images.size() - 1) {
                    allText.append("\n\n--- 第 ").append(i + 1).append(" 页 ---\n\n");
                }
            }
        }
        
        log.info("批量识别完成，总文本长度：{} 字符", allText.length());
        return allText.toString();
    }

    @Override
    public String recognizeWithModel(ChatModel chatModel, String base64Image, String mimeType) throws Exception {
        log.debug("使用模型配置进行 OCR 识别：{}", chatModel.getModelName());

        // 1. 构建请求 URL
        String apiUrl = chatModel.getApiHost();
        if (!apiUrl.endsWith("/chat/completions")) {
            if (apiUrl.endsWith("/")) {
                apiUrl = apiUrl + "chat/completions";
            } else {
                apiUrl = apiUrl + "/chat/completions";
            }
        }

        // 2. 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + chatModel.getApiKey());

        // 3. 构建请求体（OpenAI 兼容格式）
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", chatModel.getModelName());
        requestBody.put("temperature", 0.01);  // 低温度保证准确性
        requestBody.put("max_tokens", 4096);

        // 构建消息内容
        List<Map<String, Object>> contentList = new ArrayList<>();
        
        // 文本内容
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", OCR_SYSTEM_PROMPT);
        contentList.add(textContent);
        
        // 图片内容
        Map<String, Object> imageUrlMap = new HashMap<>();
        imageUrlMap.put("url", pdfImageUtils.buildDataUri(base64Image, mimeType));
        
        Map<String, Object> imageContent = new HashMap<>();
        imageContent.put("type", "image_url");
        imageContent.put("image_url", imageUrlMap);
        contentList.add(imageContent);

        // 用户消息
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", contentList);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(userMessage);
        requestBody.put("messages", messages);

        // 4. 发送请求
        log.debug("发送请求到阿里云 VL 模型：{}", apiUrl);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, requestEntity, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                // 5. 解析响应
                JSONObject jsonResponse = JSON.parseObject(response.getBody());
                JSONArray choices = jsonResponse.getJSONArray("choices");
                
                if (choices != null && !choices.isEmpty()) {
                    JSONObject firstChoice = choices.getJSONObject(0);
                    JSONObject message = firstChoice.getJSONObject("message");
                    String content = message.getString("content");
                    
                    if (StringUtils.hasText(content)) {
                        log.debug("OCR 识别成功，返回文本长度：{} 字符", content.length());
                        return content.trim();
                    }
                }
                
                log.warn("OCR 识别结果为空");
                return "";
            } else {
                log.error("OCR 请求失败，状态码：{}, 响应：{}", response.getStatusCode(), response.getBody());
                throw new IOException("OCR 请求失败：" + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("调用阿里云 VL 模型异常", e);
            throw new Exception("OCR 识别失败：" + e.getMessage(), e);
        }
    }
}
