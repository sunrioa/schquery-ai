package cn.ling.mapper;

import cn.ling.domain.pojo.SegmentationWords;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author Administrator
* @description 针对表【segmentation_words(分词词库)】的数据库操作Mapper
* @createDate 2025-08-09 00:47:54
* @Entity com.example.wordfilter.domain.SegmentationWords
*/
@Mapper
public interface SegmentationWordsMapper extends BaseMapper<SegmentationWords> {
}




