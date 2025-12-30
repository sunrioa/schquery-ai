package cn.ling.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 知识库文档详情DTO
 * - 用于管理端查看/编辑文档内容与元数据，并展示处理状态
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentInfoDTO {
    private Long documentId;
    private Long knowledgeId;
    private String title;
    private String content;
    /**
     * 处理状态：0-未处理，1-处理中，2-处理完成，3-处理失败
     */
    private Integer processStatus;
    private Integer chunkCount;
    private LocalDateTime updateTime;
    private Map<String, Object> metadata;
}

