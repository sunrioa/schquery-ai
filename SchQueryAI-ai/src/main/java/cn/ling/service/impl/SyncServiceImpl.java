package cn.ling.service.impl;

import cn.ling.domain.dto.DocumentsDTO;
import cn.ling.domain.ocr.OcrResp;
import cn.ling.domain.pojo.Documents;
import cn.ling.rpc.OcrRpc;
import cn.ling.service.DocumentChunksService;
import cn.ling.service.DocumentsService;
import cn.ling.service.SyncService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;

/**
 * 同步服务实现类
 * 负责文档的异步处理，包括OCR识别、内容提取和向量化
 */
@Slf4j
@Service
public class SyncServiceImpl implements SyncService {

    @Resource
    private OcrRpc ocrRpc;

    @Resource
    private AIserviceImpl aIservice;

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

            // 2. 根据文件类型提取内容
            String content;
            try {
                if ("application/pdf".equals(file.getContentType())) {
                    log.info("开始对PDF文件进行OCR识别 - 文档ID: {}, 文件名: {}", documentsId, file.getOriginalFilename());
                    content = executeOcr(file);
                    log.info("PDF OCR识别完成 - 文档ID: {}, 提取内容长度: {} 字符", documentsId, content != null ? content.length() : 0);
                } else {
                    log.info("非PDF文件，跳过OCR处理 - 文档ID: {}, 文件类型: {}", documentsId, file.getContentType());
                    content = "";
                }
            } catch (Exception e) {
                log.error("文档内容提取失败 - 文档ID: {}, 错误信息: {}", documentsId, e.getMessage(), e);
                content = "";
            }

            // 3. 更新文档内容到数据库
            try {
                documents.setContent(content);
                documents.setProcessStatus(StringUtils.hasText(content) ? 1 : 0); // 有内容设置为已处理，无内容设置为待处理
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
                    documents.setProcessStatus(-1); // 处理失败状态
                    documentsService.updateById(documents);
                } catch (Exception updateEx) {
                    log.error("更新文档失败状态时发生异常 - 文档ID: {}, 错误信息: {}", documentsId, updateEx.getMessage(), updateEx);
                }
                return;
            }

            // 4. 调用文档分块服务进行向量化处理
            if (StringUtils.hasText(content)) {
                try {
                    log.info("开始进行文档分块和向量化处理 - 文档ID: {}", documentsId);
                    documentChunksService.saveDocument(documents.getId(), documentsDTO.getMetadata(), documents.getContent());
                    log.info("文档分块和向量化处理完成 - 文档ID: {}", documentsId);
                } catch (Exception e) {
                    log.error("文档分块和向量化处理失败 - 文档ID: {}, 错误信息: {}", documentsId, e.getMessage(), e);
                    // 标记文档处理失败
                    try {
                        documents.setProcessStatus(-1); // 处理失败状态
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
                    documents.setProcessStatus(-1); // 处理失败状态
                    documentsService.updateById(documents);
                }
            } catch (Exception updateEx) {
                log.error("更新文档失败状态时发生异常 - 文档ID: {}, 错误信息: {}", documentsId, updateEx.getMessage(), updateEx);
            }
        }
    }

    /**
     * 执行OCR识别
     * 调用RPC服务对PDF文件进行文本提取
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

            return aIservice.ocrCorrect(ocrResp.getData());

        } catch (Exception e) {
            log.error("OCR识别过程中发生异常 - 文件名: {}, 错误信息: {}",
                    pdfFile.getOriginalFilename(), e.getMessage(), e);
            throw new RuntimeException("OCR识别失败：" + e.getMessage(), e);
        }
    }
}
