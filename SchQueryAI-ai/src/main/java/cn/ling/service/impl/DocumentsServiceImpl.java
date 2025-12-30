package cn.ling.service.impl;

import cn.ling.Result;
import cn.ling.domain.dto.DocumentsDTO;
import cn.ling.service.DocumentChunksService;
import cn.ling.service.KnowledgeInfoService;
import cn.ling.service.SyncService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.ling.domain.pojo.Documents;
import cn.ling.service.DocumentsService;
import cn.ling.mapper.DocumentsMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;

/**
 * 文档服务实现类
 * 负责文档的上传、存储和异步处理
 */
@Slf4j
@Service
public class DocumentsServiceImpl extends ServiceImpl<DocumentsMapper, Documents>
    implements DocumentsService{

    @Resource
    private DocumentChunksService documentChunksService;

    @Resource
    SyncService syncService;

    @Resource
    private KnowledgeInfoService knowledgeInfoService;

    /**
     * 处理文档上传
     * 1. 保存文档基本信息到数据库
     * 2. 异步处理文档内容（OCR、分块、向量化）
     *
     * @param documentsDTO 文档数据传输对象
     * @param file 上传的文件
     * @return 上传结果
     */
    @Override
    public Result<Long> upload(DocumentsDTO documentsDTO, MultipartFile file) {
        log.info("开始处理文档上传 - 文件名: {}, 文件类型: {}, 文件大小: {} bytes",
                file.getOriginalFilename(), file.getContentType(), file.getSize());

        try {
            // 1. 参数校验
            if (file.isEmpty()) {
                log.error("文档上传失败：文件为空");
                return Result.error("文件不能为空");
            }

            if (!StringUtils.hasText(file.getOriginalFilename())) {
                log.error("文档上传失败：文件名为空");
                return Result.error("文件名不能为空");
            }

            // 2. 构建文档实体
            String title = StringUtils.hasText(documentsDTO.getTitle())
                    ? documentsDTO.getTitle()
                    : file.getOriginalFilename();

            Long knowledgeId = documentsDTO.getKnowledgeId();
            if (knowledgeId == null) {
                knowledgeId = knowledgeInfoService.ensureDefaultKnowledgeId();
            }

            Map<String, Object> metadata = documentsDTO.getMetadata() == null ? new HashMap<>() : new HashMap<>(documentsDTO.getMetadata());
            metadata.putIfAbsent("title", title);
            if (knowledgeId != null) {
                metadata.putIfAbsent("knowledge_id", knowledgeId);
            }
            if (StringUtils.hasText(file.getOriginalFilename())) {
                metadata.putIfAbsent("file_name", file.getOriginalFilename());
            }
            if (StringUtils.hasText(file.getContentType())) {
                metadata.putIfAbsent("content_type", file.getContentType());
            }
            documentsDTO.setMetadata(metadata);

            Documents documents = Documents.builder()
                    .knowledgeId(knowledgeId)
                    .fileType(file.getContentType())
                    .title(title)
                    .uploadTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .status(1) // 正常状态
                    .processStatus(1) // 已提交处理（异步处理中）
                    .metadata(metadata)
                    .build();

            // 3. 保存文档基本信息
            boolean saved = save(documents);
            if (!saved) {
                log.error("文档基本信息保存失败 - 文件名: {}", file.getOriginalFilename());
                return Result.error("文档保存失败");
            }

            log.info("文档基本信息保存成功 - 文档ID: {}, 标题: {}", documents.getId(), documents.getTitle());

            // 4. 异步处理文档内容（OCR、分块、向量化）
            try {
                syncService.doKnowledgeUpload(documents.getId(), documentsDTO, file, this, documentChunksService);
                log.info("已启动异步文档处理任务 - 文档ID: {}", documents.getId());
            } catch (Exception e) {
                log.error("启动异步文档处理任务失败 - 文档ID: {}, 错误信息: {}", documents.getId(), e.getMessage(), e);
                // 更新文档状态为处理失败
                documents.setProcessStatus(3); // 处理失败状态
                updateById(documents);
                return Result.error("文档处理启动失败：" + e.getMessage());
            }

            log.info("文档上传成功 - 文档ID: {}, 文件名: {}", documents.getId(), file.getOriginalFilename());
            return Result.success(documents.getId(), "上传成功");

        } catch (Exception e) {
            log.error("文档上传过程中发生异常 - 文件名: {}, 错误信息: {}", file.getOriginalFilename(), e.getMessage(), e);
            return Result.error("文档上传失败：" + e.getMessage());
        }
    }
}
