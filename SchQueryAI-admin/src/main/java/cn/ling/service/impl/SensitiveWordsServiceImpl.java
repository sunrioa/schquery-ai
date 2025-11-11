package cn.ling.service.impl;

import cn.ling.domain.pojo.SensitiveWords;
import cn.ling.mapper.SensitiveWordsMapper;
import cn.ling.service.SensitiveWordsService;
import cn.ling.utils.WordsFilterUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 敏感词服务实现类
 * 负责敏感词库的数据库操作及敏感词过滤工具的初始化
 */
@Slf4j
@Service
public class SensitiveWordsServiceImpl extends ServiceImpl<SensitiveWordsMapper, SensitiveWords>
        implements SensitiveWordsService, InitializingBean {

    @Autowired
    private WordsFilterUtils wordsFilterUtils;

    /**
     * 获取所有启用状态的敏感词
     * 用于加载敏感词词典，构建敏感词过滤前缀树
     *
     * @return 启用状态的敏感词列表
     */
    @Override
    public List<String> getWords() {
        return lambdaQuery()
                .eq(SensitiveWords::getStatus, 1) // 仅查询状态为1（启用）的敏感词
                .list()
                .stream()
                .map(SensitiveWords::getWord) // 提取敏感词内容
                .toList();
    }

    /**
     * 批量加载敏感词到数据库
     * 用于初始化或更新敏感词库，默认将状态设置为启用（1）
     *
     * @param words 待加载的敏感词列表
     */
    @Transactional // 保证批量插入的事务一致性
    @Override
    public void loadData(List<String> words) {
        saveBatch(
                words.stream()
                        .map(word -> SensitiveWords.builder()
                                .word(word)
                                .status(1) // 默认为启用状态
                                .build())
                        .toList()
        );
        log.info("批量加载敏感词完成，共加载 {} 条数据", words.size());
    }

    @Override
    public void afterPropertiesSet() {
        wordsFilterUtils.init(getWords());
    }
}