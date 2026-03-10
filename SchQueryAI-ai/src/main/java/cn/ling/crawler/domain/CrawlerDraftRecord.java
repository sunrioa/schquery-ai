package cn.ling.crawler.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 爬虫抓取草稿记录实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("crawler_draft_record")
public class CrawlerDraftRecord {

    /**
     * 草稿ID（UUID）
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 页面URL
     */
    private String url;

    /**
     * 页面标题
     */
    private String title;

    /**
     * 页面内容
     */
    private String content;

    /**
     * 内容长度
     */
    private Integer contentLength;

    /**
     * 抓取时间
     */
    private LocalDateTime fetchTime;

    /**
     * 草稿更新时间
     */
    private LocalDateTime updatedTime;

    /**
     * 是否编辑：0-否，1-是
     */
    private Integer edited;

    /**
     * 是否已提交入库：0-否，1-是
     */
    private Integer saved;

    /**
     * 提交入库时间
     */
    private LocalDateTime savedTime;

    /**
     * 目标知识库ID
     */
    private Long knowledgeId;
}
