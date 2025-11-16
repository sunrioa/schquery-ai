package cn.ling.service.impl;

import cn.ling.Result;
import cn.ling.domain.dto.DocumentsDTO;
import cn.ling.domain.ocr.OcrResp;
import cn.ling.rpc.OcrRpc;
import cn.ling.service.DocumentChunksService;
import cn.ling.sync.SyncService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.ling.domain.pojo.Documents;
import cn.ling.service.DocumentsService;
import cn.ling.mapper.DocumentsMapper;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

/**
* @author Administrator
* @description 针对表【documents】的数据库操作Service实现
* @createDate 2025-11-16 00:26:43
*/
@Service
public class DocumentsServiceImpl extends ServiceImpl<DocumentsMapper, Documents>
    implements DocumentsService{

    @Resource
    private DocumentChunksService documentChunksService;

    @Resource
    SyncService syncService;

    @Override
    public Result<String> upload(DocumentsDTO documentsDTO,MultipartFile file) {
        Documents documents = Documents.builder()
                .fileType(file.getContentType())
                .title(!documentsDTO.getTitle().isEmpty()?documentsDTO.getTitle():file.getOriginalFilename())
                .uploadTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .status(1)
                .processStatus(0)
                .metadata(documentsDTO.getMetadata())
                .build();
        save(documents);
        syncService.doKnowledgeUpload(documents.getId(),documentsDTO,file,this,documentChunksService);
        return Result.success("上传成功");
    }
}