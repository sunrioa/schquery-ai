package cn.ling.service;

import cn.ling.domain.pojo.SensitiveWords;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 敏感词服务接口
 * 提供敏感词管理的业务功能，包括敏感词的增删改查和词库管理
 * 继承IService接口获得基础CRUD功能，并扩展敏感词特有的业务方法
 */
public interface SensitiveWordsService extends IService<SensitiveWords>  {
    /**
     * 获取所有启用状态的敏感词
     * 用于构建敏感词过滤前缀树，为敏感词检测提供数据基础
     * 只返回启用状态（status=1）的敏感词，禁用的词不会被加载到词典中
     *
     * @return 启用状态的敏感词内容列表，用于构建敏感词过滤器
     */
    List<String> getWords();

    /**
     * 批量加载敏感词到数据库
     * 用于系统初始化或批量更新敏感词库，新加载的敏感词默认为启用状态
     * 支持敏感词词库的快速初始化和批量维护
     *
     * @param words 待批量加载的敏感词列表，每个敏感词会自动创建为启用状态
     */
    void loadData(List<String> words);
}