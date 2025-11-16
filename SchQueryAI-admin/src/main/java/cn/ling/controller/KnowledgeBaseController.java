package cn.ling.controller;

import cn.ling.Result;
import cn.ling.domain.dto.DocumentsDTO;
import cn.ling.service.DocumentsService;
import cn.ling.utils.JsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库管理控制器
 * 提供文档上传、管理等功能
 *
 * @author Administrator
 */
@Slf4j
@RestController
@RequestMapping("/admin/knowledge")
public class KnowledgeBaseController {

    @Resource
    private DocumentsService documentsService;

    /**
     * 上传文档到知识库
     * 支持PDF等文档格式，自动进行OCR处理和向量化存储
     *
     * @param id 文档ID（可选）
     * @param title 文档标题（可选，默认使用文件名）
     * @param content 文档内容（可选）
     * @param metadataJson 元数据JSON字符串（可选）
     * @param file 上传的文件（必填）
     * @return 上传结果
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> upload(
            @RequestParam(value = "id", required = false) Long id,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "metadata", required = false) String metadataJson,
            @RequestParam("file") MultipartFile file) {

        log.info("开始处理文档上传请求 - 文件名: {}, 文件大小: {} bytes, 文件类型: {}",
                file.getOriginalFilename(), file.getSize(), file.getContentType());

        try {
            // 1. 参数校验
            if (file.isEmpty()) {
                log.error("上传失败：文件为空");
                return Result.error("文件不能为空");
            }

            // 2. 封装DocumentsDTO对象
            DocumentsDTO documentsDTO = new DocumentsDTO();
            documentsDTO.setId(id);
            documentsDTO.setTitle(title);
            documentsDTO.setContent(content);

            // 3. 解析元数据JSON
            try {
                documentsDTO.setMetadata(JsonUtils.strToMap(metadataJson));
                log.debug("元数据解析成功: {}", documentsDTO.getMetadata());
            } catch (Exception e) {
                log.warn("元数据解析失败，将使用空元数据: {}", e.getMessage());
                documentsDTO.setMetadata(null);
            }

            // 4. 调用服务层方法
            log.info("调用服务层处理文档上传 - 文档ID: {}, 标题: {}", id, title);

            return documentsService.upload(documentsDTO, file);

        } catch (Exception e) {
            log.error("文档上传过程中发生异常 - 文件名: {}, 错误信息: {}", file.getOriginalFilename(), e.getMessage(), e);
            return Result.error("文档上传失败：" + e.getMessage());
        }
    }
}
