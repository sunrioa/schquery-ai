package cn.ling.service.impl;

import cn.ling.domain.dto.DocumentsDTO;
import cn.ling.domain.ocr.OcrResp;
import cn.ling.domain.pojo.Documents;
import cn.ling.rpc.OcrRpc;
import cn.ling.service.DocumentChunksService;
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
import java.util.Map;

/**
 * 同步服务实现类
 * 负责文档的异步处理，包括OCR识别、内容提取和向量化
 */
@Slf4j
@Service
public class SyncServiceImpl implements SyncService {

    @Resource
    private OcrRpc ocrRpc;

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
            return executeOcr(file);
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
                return executeOcr(file);
            }
        }

        return content != null ? content : "";
    }

    /**
     * 执行OCR识别
     * 调用RPC服务对PDF文件进行文本提取
     * 主要用于扫描版PDF或图片类文档
     *
     * @param pdfFile PDF文件
     * @return 提取的文本内容
     * @throws RuntimeException OCR识别失败时抛出异常
     */
    private String executeOcr(MultipartFile pdfFile) {
        log.debug("开始执行OCR识别 - 文件名: {}, 文件大小: {} bytes", pdfFile.getOriginalFilename(), pdfFile.getSize());

        try {
            if (pdfFile.isEmpty()) {
                log.error("OCR识别失败：文件为空");
                throw new IllegalArgumentException("文件不能为空");
            }

            OcrResp ocrResp = ocrRpc.getOcrResult(pdfFile);
            if (ocrResp == null) {
                log.error("OCR识别失败：响应为空 - 文件名: {}", pdfFile.getOriginalFilename());
                throw new RuntimeException("OCR响应为空");
            }

            if (!StringUtils.hasText(ocrResp.getData())) {
                log.warn("OCR识别结果为空 - 文件名: {}", pdfFile.getOriginalFilename());
                return "";
            }

            log.debug("OCR识别成功 - 文件名: {}, 提取文本长度: {} 字符",
                    pdfFile.getOriginalFilename(), ocrResp.getData().length());

            // OCR 结果后处理：清理和规范化文本
            String processedContent = postProcessOcrResult(ocrResp.getData());
            return processedContent;

        } catch (Exception e) {
            log.error("OCR识别过程中发生异常 - 文件名: {}, 错误信息: {}",
                    pdfFile.getOriginalFilename(), e.getMessage(), e);
            throw new RuntimeException("OCR识别失败：" + e.getMessage(), e);
        }
    }

    /**
     * OCR 结果后处理
     * 清理和规范化 OCR 识别的文本内容
     *
     * @param ocrResult OCR 原始识别结果
     * @return 处理后的文本
     */
    private String postProcessOcrResult(String ocrResult) {
        if (!StringUtils.hasText(ocrResult)) {
            return ocrResult;
        }

        // 1. 去除多余的空白字符
        String processed = ocrResult.replaceAll("\\s+", " ").trim();

        // 2. 修正常见的 OCR 识别错误
        processed = processed
                // 修正连续的句号
                .replaceAll("\\.{3,}", "…")
                // 修正连续的连字符
                .replaceAll("-{3,}", "—")
                // 修正错误的换行合并（小写字母后跟大写字母可能是错误的换行）
                .replaceAll("([a-z])(\\s*)([A-Z])", "$1$2 $3");

        // 3. 去除控制字符
        processed = processed.replaceAll("[\\p{Cntrl}]", " ");

        // 4. 去除特殊字符序列（保留有意义的标点）
        processed = processed.replaceAll("[^\\p{L}\\p{N}\\p{P}\\s]", " ");

        // 5. 再次清理空白
        processed = processed.replaceAll("\\s+", " ").trim();

        log.debug("OCR 结果后处理完成 - 原始长度: {}, 处理后长度: {}",
                ocrResult.length(), processed.length());

        return processed;
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
        log.info("爬虫直接保存内容 - 知识库ID: {}, 内容长度: {}", knowledgeId, content.length());

        try {

            // 1. 创建 Documents 对象
            Documents documents = new Documents();
            documents.setKnowledgeId(knowledgeId);
            documents.setProcessStatus(1); // 处理中
            documents.setUpdateTime(LocalDateTime.now());

            // 2. 保存基本信息
            boolean saved = documentsService.save(documents);
            if (!saved) {
                throw new RuntimeException("保存文档失败");
            }

            // 3. 更新内容
            documents.setContent(content);
            documents.setMetadata(metadata);
            documents.setProcessStatus(2); // 已处理
            documents.setUpdateTime(LocalDateTime.now());

            // 3.5 提取 sourceUrl 并设置到 Documents.sourceUrl
            if (metadata != null && !metadata.isEmpty()) {
                Object sourceUrlObj = metadata.get("sourceUrl");
                if (sourceUrlObj != null) {
                    documents.setSourceUrl(sourceUrlObj.toString());
                }

                // 3.5.1 提取 sourceType 并设置到 Documents.sourceType
                Object sourceTypeObj = metadata.get("sourceType");
                if (sourceTypeObj != null) {
                    try {
                        documents.setSourceType(Integer.parseInt(sourceTypeObj.toString()));
                    } catch (NumberFormatException e) {
                        log.warn("sourceType 格式错误: {}", sourceTypeObj);
                    }
                }
            }

            // 3.6 提取 title 并设置到 Documents.title
            String title = null;
            if (metadata != null && !metadata.isEmpty()) {
                Object titleObj = metadata.get("title");
                if (titleObj != null) {
                    title = titleObj.toString();
                }
            }
            if (title != null && !title.isEmpty()) {
                documents.setTitle(title);
            }

            boolean updated = documentsService.updateById(documents);
            if (!updated) {
                throw new RuntimeException("更新文档内容失败");
            }

            // 4. 如果有内容，进行向量化处理
            if (StringUtils.hasText(content)) {
                log.info("开始文档分块和向量化处理 - 文档ID: {}", documents.getId());
                documentChunksService.saveDocument(
                        documents.getId(),
                        knowledgeId,
                        metadata,
                        content
                );
                log.info("文档分块和向量化处理完成 - 文档ID: {}", documents.getId());

                // 5. 标记处理完成
                documents.setProcessStatus(2); // 已完成
                documents.setUpdateTime(LocalDateTime.now());
                documentsService.updateById(documents);
                log.info("爬虫内容保存完成 - 文档ID: {}, 内容长度: {}", documents.getId(), content.length());
            } else {
                log.warn("文档内容为空，跳过分块和向量化处理 - 文档ID: {}", documents.getId());
            }

        } catch (Exception e) {
            log.error("爬虫内容保存失败 - 知识库ID: {}, 错误信息: {}", knowledgeId, e.getMessage(), e);
            // 尝试更新为处理失败
            try {
                Documents documents = documentsService.getById(knowledgeId);
                if (documents != null) {
                    documents.setProcessStatus(3); // 处理失败
                    documents.setUpdateTime(LocalDateTime.now());
                    documentsService.updateById(documents);
                }
            } catch (Exception updateEx) {
                log.error("更新文档失败状态时发生异常 - 知识库ID: {}, 错误信息: {}", knowledgeId, updateEx.getMessage(), updateEx);
            }
            throw new RuntimeException("爬虫内容保存失败：" + e.getMessage(), e);
        }
    }
}
