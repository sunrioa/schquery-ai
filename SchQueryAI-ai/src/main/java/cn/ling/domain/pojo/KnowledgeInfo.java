package cn.ling.domain.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库表：knowledge_info
 */
@TableName(value = "knowledge_info")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeInfo {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String kname;
    private String description;
    private String systemPrompt;
    private Integer textBlockSize;
    private Integer overlapChar;
    private Integer retrieveLimit;
    private Integer useRerank;
    private String rerankModelName;
    private Integer candidateCount;
    private Double minScore;
    private String vectorModelName;
    private String embeddingModelName;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

