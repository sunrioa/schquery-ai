package cn.ling.controller;

import cn.ling.Result;
import cn.ling.domain.dto.DocumentsDTO;
import cn.ling.service.DocumentsService;
import cn.ling.utils.JsonUtils;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/knowledge")
public class KnowledgeBaseController {

    @Resource
    private DocumentsService documentsService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> upload(
            @RequestParam(value = "id", required = false) Long id,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "metadata", required = false) String metadataJson,
            @RequestParam("file") MultipartFile file) {

        // 1. 封装DocumentsDTO对象
        DocumentsDTO documentsDTO = new DocumentsDTO();
        documentsDTO.setId(id);
        documentsDTO.setTitle(title);
        documentsDTO.setContent(content);
        documentsDTO.setMetadata(JsonUtils.strToMap(metadataJson));
        // 3. 调用服务层方法
        return documentsService.upload(documentsDTO, file);
    }
}
