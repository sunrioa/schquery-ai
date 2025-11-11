package cn.ling.service;

import cn.ling.domain.pojo.SensitiveWords;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * @author Administrator
 * @description 针对表【t_sensitive_word(敏感词库表，支持分类、匹配模式和变体处理)】的数据库操作Service
 * @createDate 2025-08-07 22:15:29
 */
public interface SensitiveWordsService extends IService<SensitiveWords>  {
    /**
     * 获取所有启用状态的敏感词
     * 用于加载敏感词词典，构建敏感词过滤前缀树
     *
     * @return 启用状态的敏感词列表
     */
    List<String> getWords();

    /**
     * 批量加载敏感词到数据库
     * 用于初始化或更新敏感词库，默认将状态设置为启用（1）
     *
     * @param words 待加载的敏感词列表
     */
    void loadData(List<String> words);
}