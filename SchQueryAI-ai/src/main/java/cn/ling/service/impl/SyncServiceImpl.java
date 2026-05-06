package cn.ling.service.impl;

import cn.ling.crawler.util.CrawlerUrlUtils;
import cn.ling.domain.dto.DocumentsDTO;
import cn.ling.domain.pojo.Documents;
import cn.ling.service.DocumentChunksService;
import cn.ling.service.OcrService;
import cn.ling.service.DocumentsService;
import cn.ling.service.SyncService;
import cn.ling.utils.DocumentReaderStrategy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 同步服务实现类
 * 负责文档的异步处理，包括OCR识别、内容提取和向量化
 */
@Slf4j
@Service
public class SyncServiceImpl implements SyncService {

    @Resource
    private OcrService ocrService;

    private final ConcurrentMap<String, Object> crawlerDocumentLocks = new ConcurrentHashMap<>();

    /**
     * 异步处理知识库文档上传
     * 1. 对PDF文件进行OCR识别提取文本内容
     * 2. 更新文档内容到数据库
     * 3. 调用文档分块服务进行向量化处理
     *
     * @param documentsId 文档ID
     * @param documentsDTO 文档数据传输对象
     * @param file 上传的文件
     * @param documentsService 文档服务
     * @param documentChunksService 文档分块服务
     */
    @Override
    @Async
    public void doKnowledgeUpload(Long documentsId, DocumentsDTO documentsDTO, MultipartFile file,
                                  DocumentsService documentsService, DocumentChunksService documentChunksService) {
        log.info("开始异步处理文档 - 文档ID: {}, 文件名: {}, 文件类型: {}",
                documentsId, file.getOriginalFilename(), file.getContentType());

        try {
            // 1. 参数校验
            if (documentsId == null) {
                log.error("异步文档处理失败：文档ID为空");
                return;
            }

            Documents documents = documentsService.getById(documentsId);
            if (documents == null) {
                log.error("异步文档处理失败：未找到文档 - 文档ID: {}", documentsId);
                return;
            }

            // 2. 标记处理中
            try {
                documents.setProcessStatus(1);
                documents.setUpdateTime(LocalDateTime.now());
                documentsService.updateById(documents);
            } catch (Exception e) {
                log.warn("更新文档处理中状态失败（继续处理） - 文档ID: {}, 错误信息: {}", documentsId, e.getMessage(), e);
            }

            // 3. 根据入参/文件类型提取内容（优先使用前端直接传入的content）
            String content = "";
            try {
                if (documentsDTO != null && StringUtils.hasText(documentsDTO.getContent())) {
                    // 优先使用前端传入的content
                    content = documentsDTO.getContent().trim();
                    log.info("使用请求参数content作为文档内容 - 文档ID: {}, 内容长度: {} 字符", documentsId, content.length());
                } else {
                    // 使用文档读取策略中心提取内容
                    content = extractContentByStrategy(documentsId, file);
                }
            } catch (Exception e) {
                log.error("文档内容提取失败 - 文档ID: {}, 错误信息: {}", documentsId, e.getMessage(), e);
                content = "";
            }

            // 4. 更新文档内容到数据库
            try {
                documents.setContent(content);
                // 有内容继续保持“处理中”，无内容回退为“未处理”（便于后续补传content）
                documents.setProcessStatus(StringUtils.hasText(content) ? 1 : 0);
                documents.setUpdateTime(LocalDateTime.now());
                boolean updated = documentsService.updateById(documents);
                if (updated) {
                    log.info("文档内容更新成功 - 文档ID: {}, 内容长度: {} 字符", documentsId, content.length());
                } else {
                    log.error("文档内容更新失败 - 文档ID: {}", documentsId);
                    return;
                }
            } catch (Exception e) {
                log.error("文档内容更新异常 - 文档ID: {}, 错误信息: {}", documentsId, e.getMessage(), e);
                // 标记文档处理失败
                try {
                    documents.setProcessStatus(3); // 处理失败状态
                    documentsService.updateById(documents);
                } catch (Exception updateEx) {
                    log.error("更新文档失败状态时发生异常 - 文档ID: {}, 错误信息: {}", documentsId, updateEx.getMessage(), updateEx);
                }
                return;
            }

            // 5. 调用文档分块服务进行向量化处理
            if (StringUtils.hasText(content)) {
                try {
                    log.info("开始进行文档分块和向量化处理 - 文档ID: {}", documentsId);
                    documentChunksService.saveDocument(
                            documents.getId(),
                            documents.getKnowledgeId(),
                            documentsDTO == null ? null : documentsDTO.getMetadata(),
                            documents.getContent()
                    );
                    log.info("文档分块和向量化处理完成 - 文档ID: {}", documentsId);

                    // 标记处理完成
                    try {
                        documents.setProcessStatus(2);
                        documents.setUpdateTime(LocalDateTime.now());
                        documentsService.updateById(documents);
                    } catch (Exception e) {
                        log.warn("更新文档处理完成状态失败 - 文档ID: {}, 错误信息: {}", documentsId, e.getMessage(), e);
                    }
                } catch (Exception e) {
                    log.error("文档分块和向量化处理失败 - 文档ID: {}, 错误信息: {}", documentsId, e.getMessage(), e);
                    // 标记文档处理失败
                    try {
                        documents.setProcessStatus(3); // 处理失败状态
                        documentsService.updateById(documents);
                    } catch (Exception updateEx) {
                        log.error("更新文档失败状态时发生异常 - 文档ID: {}, 错误信息: {}", documentsId, updateEx.getMessage(), updateEx);
                    }
                }
            } else {
                log.warn("文档内容为空，跳过分块和向量化处理 - 文档ID: {}", documentsId);
            }

            log.info("文档异步处理完成 - 文档ID: {}", documentsId);

        } catch (Exception e) {
            log.error("文档异步处理过程中发生未知异常 - 文档ID: {}, 错误信息: {}", documentsId, e.getMessage(), e);
            // 尝试更新文档状态为处理失败
            try {
                Documents documents = documentsService.getById(documentsId);
                if (documents != null) {
                    documents.setProcessStatus(3); // 处理失败状态
                    documentsService.updateById(documents);
                }
            } catch (Exception updateEx) {
                log.error("更新文档失败状态时发生异常 - 文档ID: {}, 错误信息: {}", documentsId, updateEx.getMessage(), updateEx);
            }
        }
    }

    /**
     * 使用文档读取策略中心提取内容
     * 1. 优先使用DocumentReaderStrategy直接提取文档文字内容
     * 2. 对于PDF文件，如果提取内容为空或太少（判定为扫描件），则调用OCR服务
     *
     * @param documentsId 文档ID
     * @param file 上传的文件
     * @return 提取的文本内容
     */
    private String extractContentByStrategy(Long documentsId, MultipartFile file) {
        log.info("开始使用文档读取策略提取内容 - 文档ID: {}, 文件名: {}", documentsId, file.getOriginalFilename());

        // 1. 检查文件类型是否支持
        if (!DocumentReaderStrategy.isSupported(file)) {
            log.warn("不支持的文件类型，尝试OCR处理 - 文档ID: {}, 文件名: {}",
                    documentsId, file.getOriginalFilename());
            return ocrService.doOcr(file);
        }

        // 2. 使用文档读取策略中心提取内容
        String content = DocumentReaderStrategy.read(file);
        String fileType = DocumentReaderStrategy.getFileType(file);

        if (StringUtils.hasText(content)) {
            log.info("文档读取策略提取成功 - 文档ID: {}, 文件类型: {}, 内容长度: {} 字符",
                    documentsId, fileType, content.length());
        } else {
            log.warn("文档读取策略提取内容为空 - 文档ID: {}, 文件类型: {}", documentsId, fileType);
        }

        // 3. 对于PDF文件，如果提取的内容太少，可能是扫描件，需要使用OCR
        if ("application/pdf".equals(fileType)) {
            // 判断是否为扫描件：提取内容少于50字符，或者去除空白后少于20字符
            boolean isLikelyScan = content == null
                    || content.trim().length() < 20
                    || content.length() < 50;

            if (isLikelyScan) {
                log.info("PDF提取内容过少，判定为扫描件，启用OCR识别 - 文档ID: {}, 已提取长度: {} 字符",
                        documentsId, content != null ? content.length() : 0);
                return ocrService.doOcr(file);
            }
        }

        return content != null ? content : "";
    }

    /**
     * 从爬虫直接保存文档内容到知识库
     * 用于爬虫服务直接调用，跳过文件上传步骤
     *
     * @param knowledgeId 知识库 ID
     * @param content 文档内容
     * @param metadata 元数据（Map 格式）
     * @param documentsService 文档服务
     * @param documentChunksService 文档分块服务
     */
    @Override
    @Async
    public void saveCrawlerContent(Long knowledgeId, String content, Map<String, Object> metadata,
                                   DocumentsService documentsService, DocumentChunksService documentChunksService) {
        String normalizedContent = content == null ? "" : content;
        log.info("爬虫直接保存内容 - 知识库ID: {}, 内容长度: {}", knowledgeId, normalizedContent.length());

        Map<String, Object> normalizedMetadata = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
        String canonicalSourceUrl = normalizeCrawlerSourceUrl(normalizedMetadata);
        Integer sourceType = extractCrawlerSourceType(normalizedMetadata);
        String title = extractTextValue(normalizedMetadata.get("title"));

        String lockKey = buildCrawlerDocumentLockKey(knowledgeId, canonicalSourceUrl);
        Object lock = crawlerDocumentLocks.computeIfAbsent(lockKey, key -> new Object());
        Documents documents = null;

        try {
            synchronized (lock) {
                Documents existing = findExistingCrawlerDocument(knowledgeId, canonicalSourceUrl, sourceType, documentsService);
                if (existing != null) {
                    if (existing.getProcessStatus() != null && existing.getProcessStatus() == 1) {
                        log.info("检测到同一 URL 文档正在处理中，跳过重复入库: knowledgeId={}, sourceUrl={}, documentId={}",
                                knowledgeId, canonicalSourceUrl, existing.getId());
                        return;
                    }

                    long existingChunkCount = countDocumentChunks(existing.getId(), documentChunksService);
                    if (existingChunkCount > 0) {
                        log.info("检测到同一 URL 文档已存在有效分块，跳过重复入库: knowledgeId={}, sourceUrl={}, documentId={}",
                                knowledgeId, canonicalSourceUrl, existing.getId());
                        return;
                    }

                    documents = existing;
                    log.info("复用未完成的爬虫文档记录，准备重新处理: knowledgeId={}, sourceUrl={}, documentId={}",
                            knowledgeId, canonicalSourceUrl, documents.getId());
                } else {
                    documents = new Documents();
                    documents.setUploadTime(LocalDateTime.now());
                }

                LocalDateTime now = LocalDateTime.now();
                documents.setKnowledgeId(knowledgeId);
                documents.setTitle(title);
                documents.setContent(normalizedContent);
                documents.setMetadata(normalizedMetadata);
                documents.setSourceUrl(canonicalSourceUrl);
                documents.setSourceType(sourceType);
                documents.setStatus(1);
                documents.setUpdateTime(now);
                documents.setProcessStatus(StringUtils.hasText(normalizedContent) ? 1 : 0);

                boolean persisted;
                if (documents.getId() == null) {
                    persisted = documentsService.save(documents);
                } else {
                    persisted = documentsService.updateById(documents);
                }
                if (!persisted || documents.getId() == null) {
                    throw new RuntimeException("保存文档失败");
                }

                if (!StringUtils.hasText(normalizedContent)) {
                    log.warn("文档内容为空，跳过分块和向量化处理 - 文档ID: {}", documents.getId());
                    return;
                }

                log.info("开始文档分块和向量化处理 - 文档ID: {}", documents.getId());
                documentChunksService.saveDocument(
                        documents.getId(),
                        knowledgeId,
                        normalizedMetadata,
                        normalizedContent
                );
                log.info("文档分块和向量化处理完成 - 文档ID: {}", documents.getId());

                documents.setProcessStatus(2);
                documents.setUpdateTime(LocalDateTime.now());
                documentsService.updateById(documents);
                log.info("爬虫内容保存完成 - 文档ID: {}, 内容长度: {}", documents.getId(), normalizedContent.length());
            }
        } catch (Exception e) {
            log.error("爬虫内容保存失败 - 知识库ID: {}, sourceUrl={}, 错误信息: {}",
                    knowledgeId, canonicalSourceUrl, e.getMessage(), e);
            markCrawlerDocumentFailed(documents == null ? null : documents.getId(), documentsService);
            throw new RuntimeException("爬虫内容保存失败：" + e.getMessage(), e);
        } finally {
            crawlerDocumentLocks.remove(lockKey, lock);
        }
    }

    private String normalizeCrawlerSourceUrl(Map<String, Object> metadata) {
        if (metadata == null) {
            return "";
        }
        String canonicalSourceUrl = CrawlerUrlUtils.canonicalize(extractTextValue(metadata.get("sourceUrl")));
        if (StringUtils.hasText(canonicalSourceUrl)) {
            metadata.put("sourceUrl", canonicalSourceUrl);
        }
        return canonicalSourceUrl;
    }

    private Integer extractCrawlerSourceType(Map<String, Object> metadata) {
        Integer sourceType = parseIntegerValue(metadata == null ? null : metadata.get("sourceType"), 2);
        if (metadata != null) {
            metadata.put("sourceType", sourceType);
        }
        return sourceType;
    }

    private String extractTextValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Integer parseIntegerValue(Object value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String buildCrawlerDocumentLockKey(Long knowledgeId, String canonicalSourceUrl) {
        return String.valueOf(knowledgeId) + "::"
                + (StringUtils.hasText(canonicalSourceUrl) ? canonicalSourceUrl : "blank-source-url");
    }

    private Documents findExistingCrawlerDocument(Long knowledgeId, String canonicalSourceUrl, Integer sourceType,
                                                  DocumentsService documentsService) {
        if (knowledgeId == null || !StringUtils.hasText(canonicalSourceUrl)) {
            return null;
        }

        Set<String> urlCandidates = CrawlerUrlUtils.buildMatchCandidates(canonicalSourceUrl);
        List<Documents> matches = documentsService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Documents>()
                        .eq(Documents::getKnowledgeId, knowledgeId)
                        .eq(Documents::getSourceType, sourceType)
                        .in(Documents::getSourceUrl, urlCandidates)
                        .and(wrapper -> wrapper.eq(Documents::getStatus, 1).or().isNull(Documents::getStatus))
                        .orderByDesc(Documents::getId)
        );
        if (matches == null || matches.isEmpty()) {
            return null;
        }
        return matches.stream()
                .filter(item -> canonicalSourceUrl.equals(item.getSourceUrl()))
                .findFirst()
                .orElse(matches.get(0));
    }

    private long countDocumentChunks(Long documentId, DocumentChunksService documentChunksService) {
        if (documentId == null) {
            return 0L;
        }
        Long count = documentChunksService.lambdaQuery()
                .eq(cn.ling.domain.pojo.DocumentChunks::getDocumentId, documentId)
                .count();
        return count == null ? 0L : count;
    }

    private void markCrawlerDocumentFailed(Long documentId, DocumentsService documentsService) {
        if (documentId == null) {
            return;
        }
        try {
            Documents documents = documentsService.getById(documentId);
            if (documents == null) {
                return;
            }
            documents.setProcessStatus(3);
            documents.setUpdateTime(LocalDateTime.now());
            documentsService.updateById(documents);
        } catch (Exception updateEx) {
            log.error("更新文档失败状态时发生异常 - 文档ID: {}, 错误信息: {}", documentId, updateEx.getMessage(), updateEx);
        }
    }
}
