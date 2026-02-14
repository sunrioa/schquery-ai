package cn.ling.service;

import cn.ling.domain.dto.DocumentsDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface SyncService {

    void doKnowledgeUpload(Long documentsId, DocumentsDTO documentsDTO, MultipartFile file, DocumentsService documentsService, DocumentChunksService documentChunksService);

    /**
     * 从爬虫直接保存文档内容到知识库
     * 用于爬虫服务直接调用，跳过文件上传步骤
     *
     * @param knowledgeId 知识库 ID
     * @param content 文档内容
     * @param metadata 元数据（JSON 格式）
     * @param documentsService 文档服务
     * @param documentChunksService 文档分块服务
     */
    void saveCrawlerContent(Long knowledgeId, String content, Map<String, Object> metadata,
                          DocumentsService documentsService, DocumentChunksService documentChunksService);

}
