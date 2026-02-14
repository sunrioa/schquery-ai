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
 * 爬虫 URL 访问记录实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("crawler_url_record")
public class CrawlerUrlRecord {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 访问的 URL
     */
    private String url;

    /**
     * 页面标题
     */
    private String title;

    /**
     * 状态：1-成功，2-失败
     */
    private Integer status;

    /**
     * 失败原因
     */
    private String errorMsg;

    /**
     * 访问时间
     */
    private LocalDateTime visitTime;
}
