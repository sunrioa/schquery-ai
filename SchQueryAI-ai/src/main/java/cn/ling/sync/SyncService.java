package cn.ling.sync;

import cn.ling.domain.dto.DocumentsDTO;
import cn.ling.service.DocumentChunksService;
import cn.ling.service.DocumentsService;
import org.springframework.web.multipart.MultipartFile;

public interface SyncService {

    void doKnowledgeUpload(Long documentsId, DocumentsDTO documentsDTO, MultipartFile file, DocumentsService documentsService, DocumentChunksService documentChunksService);

}
