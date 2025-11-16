package cn.ling.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentsDTO {
    /**
     * 文档唯一标识ID
     */
    private Long id;

    /**
     * 文档标题，默认为文件名
     */
    private String title;

    /**
     * 文档完整内容（小文件直接存储文本，大文件建议仅存关键摘要）
     */
    private String content;

    /**
     * 文档级扩展元数据（如作者、来源、权限标签等，按需动态存储）
     */
    private Map<String,Object> metadata;
}
