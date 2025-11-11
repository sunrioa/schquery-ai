package cn.ling.mapper;

import cn.ling.domain.pojo.SensitiveWords;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author Administrator
* @description 针对表【sensitive_words(敏感词词库)】的数据库操作Mapper
* @createDate 2025-08-07 22:15:29
* @Entity generator.domain.SensitiveWords
*/
@Mapper
public interface SensitiveWordsMapper extends BaseMapper<SensitiveWords> {
}




