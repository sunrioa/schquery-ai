package cn.ling.domain.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import java.util.Map;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 
 * @TableName document_chunks
 */
@TableName(value ="document_chunks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunks {
    /**
     * 片段唯一标识ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的文档ID，对应documents表的id
     */
    private Long documentId;

    /**
     * 片段在文档中的顺序编号（从0开始），用于重组完整文档
     */
    private Integer chunkIndex;

    /**
     * 片段具体内容（生成向量的原始文本）
     */
    private String chunkContent;

    /**
     * 片段的长度（控制embedding模型输入长度，避免超限）
     */
    private Integer chunkLength;

    /**
     * 片段创建时间（拆分完成时自动记录）
     */
    private LocalDateTime createdTime;

    /**
     * 关联Qdrant向量数据库中该片段的point ID，用于向量检索后溯源
     */
    private String qdrantPointId;

    /**
     * 片段级扩展元数据（如页码、段落位置、关键词等）
     */
    @TableField(typeHandler = JacksonTypeHandler.class) // 关键：指定JSON处理器
    private Map<String, Object> metadata;
}