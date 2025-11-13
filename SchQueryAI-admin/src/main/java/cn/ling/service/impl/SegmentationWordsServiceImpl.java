package cn.ling.service.impl;

import cn.ling.domain.pojo.SegmentationWords;
import cn.ling.mapper.SegmentationWordsMapper;
import cn.ling.service.SegmentationWordsService;
import cn.ling.utils.SegmentationUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * 分词词库服务实现类
 * 继承MyBatis-Plus的ServiceImpl，提供分词词库的数据库操作及初始化能力
 */
@Slf4j // 启用SLF4J日志功能
@Service
public class SegmentationWordsServiceImpl extends ServiceImpl<SegmentationWordsMapper, SegmentationWords>
        implements SegmentationWordsService, InitializingBean {

    @Autowired
    SegmentationUtils segmentationUtils;

    /**
     * 获取所有启用状态的分词词
     * 用于加载分词词典，辅助文本分词处理
     *
     * @return 启用状态的分词词列表
     */
    @Override
    public List<String> getSegmentationWords() {
        log.info("开始获取启用状态的分词词列表");

        List<String> words = lambdaQuery()
                .eq(SegmentationWords::getStatus, 1) // 只查询状态为1（启用）的分词词
                .list()
                .stream()
                .map(SegmentationWords::getWord) // 提取分词词内容
                .toList();

        log.info("成功获取分词词列表，共{}个词汇", words.size());
        return words;
    }

    /**
     * 批量加载分词词到数据库
     * 用于初始化或更新分词词库，默认将状态设置为启用（1）
     *
     * @param words 待加载的分词词列表
     */
    @Transactional // 事务管理，确保批量操作的原子性
    @Override
    public void loadData(List<String> words) {
        log.info("开始批量加载分词词到数据库，词汇数量: {}", words.size());

        try {
            // 将字符串列表转换为SegmentationWords对象列表
            List<SegmentationWords> segmentationWords = words.stream()
                    .map(word -> SegmentationWords.builder()
                            .word(word)
                            .status(1) // 默认为启用状态
                            .build())
                    .toList();

            // 批量保存到数据库
            saveBatch(segmentationWords);
            log.info("成功批量加载{}个分词词到数据库", words.size());
        } catch (Exception e) {
            log.error("批量加载分词词到数据库失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 初始化方法：在Bean加载后执行
     * 调用分词工具类的初始化方法，将数据库中的分词词加载到分词词典
     * 确保分词工具在使用前已加载最新的分词词库
     */
    @Override
    public void afterPropertiesSet() {
        log.info("开始初始化分词词库服务");

        try {
            // 获取数据库中的分词词列表
            List<String> words = getSegmentationWords();

            // 初始化分词工具类
            segmentationUtils.init(words);

            log.info("分词词库服务初始化完成，已加载{}个分词词", words.size());
        } catch (Exception e) {
            log.error("分词词库服务初始化失败: {}", e.getMessage(), e);
            throw e;
        }
    }
}