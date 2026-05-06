package cn.ling.crawler.mapper;

import cn.ling.crawler.domain.CrawlerUrlRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 爬虫 URL 记录 Mapper
 */
@Mapper
public interface CrawlerUrlRecordMapper extends BaseMapper<CrawlerUrlRecord> {

    /**
     * 清空所有记录
     */
    int deleteAll();
}
