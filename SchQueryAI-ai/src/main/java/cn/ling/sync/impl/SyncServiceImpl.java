package cn.ling.sync.impl;

import cn.ling.domain.dto.DocumentsDTO;
import cn.ling.domain.ocr.OcrResp;
import cn.ling.domain.pojo.Documents;
import cn.ling.rpc.OcrRpc;
import cn.ling.service.DocumentChunksService;
import cn.ling.service.DocumentsService;
import cn.ling.sync.SyncService;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Service
public class SyncServiceImpl implements SyncService {

    @Resource
    private OcrRpc ocrRpc;

    @Override
    @Async
    public void doKnowledgeUpload(Long documentsId, DocumentsDTO documentsDTO, MultipartFile file, DocumentsService documentsService, DocumentChunksService documentChunksService) {
        String content;
        if ("application/pdf".equals(file.getContentType())){
            content=executeOcr(file);
        }else{
            content="";
        }
        Documents documents = documentsService.getById(documentsId);
        documents.setContent(content);
        documents.setProcessStatus(1);
        documents.setUpdateTime(LocalDateTime.now());
        documentsService.updateById(documents);
        documentChunksService.saveDocument(documents.getId(),documentsDTO.getMetadata(),documents.getContent());
    }

    public String executeOcr(MultipartFile pdfFile){
        OcrResp ocrResp = ocrRpc.getOcrResult(pdfFile);
        return ocrResp.getData();
    }
}
