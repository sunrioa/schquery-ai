package cn.ling.service;

import cn.ling.domain.pojo.SegmentationWords;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * @author Administrator
 * @description 针对表【segmentation_words(分词词库)】的数据库操作Service
 * @createDate 2025-08-09 00:47:54
 */
public interface SegmentationWordsService extends IService<SegmentationWords> {
     /**
      * 获取所有启用状态的分词词
      * @return 启用状态的分词词列表
      */
     List<String> getSegmentationWords();

     /**
      * 批量加载分词词到数据库
      * @param words 待加载的分词词列表
      */
     void loadData(List<String> words);
}