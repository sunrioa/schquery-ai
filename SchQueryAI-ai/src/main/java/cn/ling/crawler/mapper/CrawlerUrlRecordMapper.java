package cn.ling.crawler.mapper;

import cn.ling.crawler.domain.CrawlerUrlRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 爬虫 URL 记录 Mapper
 */
@Mapper
public interface CrawlerUrlRecordMapper extends BaseMapper<CrawlerUrlRecord> {

    /**
     * 检查 URL 是否已成功访问（status=1）
     * 只将成功的记录视为"已访问"，失败和跳过的记录可以重试
     */
    boolean existsSuccessfulByUrl(@Param("url") String url);

    /**
     * 清空所有记录
     */
    int deleteAll();
}
