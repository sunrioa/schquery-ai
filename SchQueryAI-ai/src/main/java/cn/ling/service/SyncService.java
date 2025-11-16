package cn.ling.service;

import cn.ling.domain.dto.DocumentsDTO;
import org.springframework.web.multipart.MultipartFile;

public interface SyncService {

    void doKnowledgeUpload(Long documentsId, DocumentsDTO documentsDTO, MultipartFile file, DocumentsService documentsService, DocumentChunksService documentChunksService);

}
