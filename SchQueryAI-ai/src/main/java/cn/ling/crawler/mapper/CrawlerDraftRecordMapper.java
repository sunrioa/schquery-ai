package cn.ling.crawler.mapper;

import cn.ling.crawler.domain.CrawlerDraftRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 爬虫草稿记录 Mapper
 */
@Mapper
public interface CrawlerDraftRecordMapper extends BaseMapper<CrawlerDraftRecord> {
}
