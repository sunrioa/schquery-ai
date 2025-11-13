package cn.ling.mapper;

import cn.ling.domain.pojo.SensitiveWords;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 敏感词数据访问层接口
 * 提供敏感词表的数据库操作方法，继承MyBatis Plus的BaseMapper获得基础CRUD功能
 * 支持敏感词信息的增删改查、条件查询、分页查询等操作
 */
@Mapper
public interface SensitiveWordsMapper extends BaseMapper<SensitiveWords> {
}




