package cn.ling.domain.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 
 * @TableName documents
 */
@TableName(value ="documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Documents {
    /**
     * 文档唯一标识ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属知识库ID，对应knowledge_info.id
     */
    private Long knowledgeId;

    /**
     * 文档标题，默认为文件名
     */
    private String title;

    /**
     * 文档完整内容（小文件直接存储文本，大文件建议仅存关键摘要）
     */
    private String content;

    /**
     * 文件存储路径（可选，如本地路径或云存储URL）
     */
    private String filePath;

    /**
     * 文件类型（可选，如pdf、docx、txt等）
     */
    private String fileType;

    /**
     * 文档上传时间
     */
    private LocalDateTime uploadTime;

    /**
     * 文档最后修改时间（内容或属性变更时更新）
     */
    private LocalDateTime updateTime;

    /**
     * 文档状态：1-有效，0-删除（逻辑删除，避免物理删除数据）
     */
    private Integer status;

    /**
     * 处理状态：0-未处理，1-处理中，2-处理完成，3-处理失败（用于跟踪文档拆分、向量化流程）
     */
    private Integer processStatus;

    /**
     * 文档级扩展元数据（如作者、来源、权限标签等，按需动态存储）
     */
    @TableField(typeHandler = JacksonTypeHandler.class) // 关键：指定JSON处理器
    private Map<String, Object> metadata;
}
