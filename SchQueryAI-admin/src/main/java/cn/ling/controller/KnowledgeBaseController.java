package cn.ling.controller;

import cn.ling.Result;
import cn.ling.domain.dto.DocumentsDTO;
import cn.ling.service.DocumentsService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/knowledge")
public class KnowledgeBaseController {

    @Resource
    private DocumentsService documentsService;

    @PostMapping("/upload")
    public Result<String> upload(@RequestBody DocumentsDTO documentsDTO){
        return documentsService.upload(documentsDTO);
    }



}
