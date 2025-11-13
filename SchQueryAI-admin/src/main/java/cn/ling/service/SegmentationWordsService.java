package cn.ling.service;

import cn.ling.domain.pojo.SegmentationWords;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 分词词库服务接口
 * 提供分词词库管理的业务功能，包括分词词汇的增删改查和词库管理
 * 继承IService接口获得基础CRUD功能，并扩展分词词库特有的业务方法
 */
public interface SegmentationWordsService extends IService<SegmentationWords> {
     /**
      * 获取所有启用状态的分词词
      * 用于构建分词词典，为文本分词处理提供数据基础
      * 只返回启用状态（status=1）的分词词汇，禁用的词不会被加载到词典中
      *
      * @return 启用状态的分词词汇内容列表，用于构建分词处理器
      */
     List<String> getSegmentationWords();

     /**
      * 批量加载分词词到数据库
      * 用于系统初始化或批量更新分词词库，新加载的分词词汇默认为启用状态
      * 支持分词词库的快速初始化和批量维护
      *
      * @param words 待批量加载的分词词汇列表，每个词汇会自动创建为启用状态
      */
     void loadData(List<String> words);
}