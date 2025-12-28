package cn.ling.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库文档处理状态DTO
 * 用于前端展示上传后处理进度（OCR/分块/向量化）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentStatusDTO {
    private Long documentId;
    private String title;
    /**
     * 处理状态：0-未处理，1-处理中，2-处理完成，3-处理失败
     */
    private Integer processStatus;
    private Integer chunkCount;
    private LocalDateTime updateTime;
}

