package cn.ling.service.impl;

import cn.ling.domain.SegmentationWords;
import cn.ling.mapper.SegmentationWordsMapper;
import cn.ling.service.SegmentationWordsService;
import cn.ling.utils.SegmentationUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 分词词库服务实现类
 * 继承MyBatis-Plus的ServiceImpl，提供分词词库的数据库操作及初始化能力
 */
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
        return lambdaQuery()
                .eq(SegmentationWords::getStatus, 1) // 只查询状态为1（启用）的分词词
                .list()
                .stream()
                .map(SegmentationWords::getWord) // 提取分词词内容
                .toList();
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
        saveBatch(
                words.stream()
                        .map(word -> SegmentationWords.builder()
                                .word(word)
                                .status(1) // 默认为启用状态
                                .build())
                        .toList()
        );
    }

    /**
     * 初始化方法：在Bean加载后执行
     * 调用分词工具类的初始化方法，将数据库中的分词词加载到分词词典
     * 确保分词工具在使用前已加载最新的分词词库
     */
    @Override
    public void afterPropertiesSet() {
        segmentationUtils.init(getSegmentationWords());
    }
}