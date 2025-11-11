package cn.ling.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.wltea.analyzer.cfg.DefaultConfig;
import org.wltea.analyzer.core.IKSegmenter;
import org.wltea.analyzer.core.Lexeme;
import org.wltea.analyzer.dic.Dictionary;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 分词工具类
 * 基于IKAnalyzer实现文本分词功能，支持自定义分词词典加载
 */
@Slf4j
@Component
public class SegmentationUtils {

    /**
     * 对输入文本进行分词处理
     * @param input 待分词的文本
     * @return 分词结果列表
     */
    public static List<String> doSegmentationWords(String input) {
        StringReader reader = new StringReader(input);
        List<String> words = new ArrayList<>();
        // 使用IK分词器，开启智能分词模式（true表示智能分词）
        IKSegmenter ikSegmenter = new IKSegmenter(reader, false);
        Lexeme lexeme;
        try {
            // 循环获取分词结果
            while ((lexeme = ikSegmenter.next()) != null) {
                words.add(lexeme.getLexemeText());
            }
        } catch (IOException e) {
            throw new RuntimeException("分词处理失败", e);
        }
//        log.info("分词结果为:{}", words);
        return words;
    }

    /**
     * 初始化分词词典
     * 从数据库加载启用的分词词，添加到IKAnalyzer的自定义词典中
     */
    public void init(List<String> segmentationWords) {
        // 获取所有启用的分词词
        // 初始化IKAnalyzer词典配置
        Dictionary.initial(DefaultConfig.getInstance());
        // 将自定义分词词添加到词典
        Dictionary.getSingleton().addWords(segmentationWords);
        log.info("已加载{}个分词词到词典", segmentationWords.size());
    }
}