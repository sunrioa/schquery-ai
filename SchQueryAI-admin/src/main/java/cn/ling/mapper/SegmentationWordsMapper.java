package cn.ling.mapper;

import cn.ling.domain.pojo.SegmentationWords;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分词词库数据访问层接口
 * 提供分词词库表的数据库操作方法，继承MyBatis Plus的BaseMapper获得基础CRUD功能
 * 支持分词词汇的增删改查、条件查询、分页查询等操作
 */
@Mapper
public interface SegmentationWordsMapper extends BaseMapper<SegmentationWords> {
}




