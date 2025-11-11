package cn.ling.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 敏感词过滤工具类
 * 基于前缀树（字典树）实现高效的敏感词检测，支持批量加载敏感词并构建前缀树
 */
@Slf4j
@Component
public class WordsFilterUtils {

    @Autowired
    private SegmentationUtils segmentationUtils;

    /**
     * 执行敏感词过滤（单线程版）
     * 1. 对输入文本进行分词处理
     * 2. 遍历每个分词，检测是否包含敏感词
     * 3. 汇总敏感词并计算各阶段耗时
     *
     * @param comment 待过滤的评论文本
     * @return 过滤结果，包含敏感词信息、处理耗时等指标
     */
    public String doFilter(String comment) {
        // 边界处理：空文本或无敏感词库时直接返回成功
        if (comment == null || comment.isEmpty()) {
            return "";
        }

        // 1. 分词处理并计时
        List<String> segmentationWords = segmentationUtils.doSegmentationWords(comment);

        // 2. 检测敏感词
        Set<String> sensitiveWords = new HashSet<>();
        for (String word : segmentationWords) {
            // 跳过空分词
            if (word == null || word.trim().isEmpty()) {
                continue;
            }
            // 检测当前分词中的敏感词并加入结果集
            List<String> foundWords = doSensitiveWordsFilter(word);
            sensitiveWords.addAll(foundWords);
        }

        return sensitiveWords.toString();
    }


    @Getter
    private static boolean isEmpty = true; // 标记敏感词库是否为空

    /**
     * 前缀树节点内部类
     * 每个节点包含子节点映射和是否为敏感词结尾的标记
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class Tree {
        private HashMap<Character, Tree> map; // 子节点映射（字符到节点的映射）
        private boolean isFinal; // 标记当前节点是否为某个敏感词的结尾
    }

    private static final HashMap<Character, Tree> sensitiveWordsTree = new HashMap<>(); // 敏感词前缀树根节点

    /**
     * 初始化敏感词库
     * 从数据库加载启用的敏感词，构建前缀树并统计数量
     */
    public void init(List<String> sensitiveWords) {
        sensitiveWords.forEach(this::buildSensitiveWordsTree); // 逐个构建前缀树节点
        int wordCount = countSensitiveWords(sensitiveWordsTree); // 统计敏感词总数
        log.info("敏感词加载完成，共加载 {} 个敏感词", wordCount);
        isEmpty = wordCount == 0; // 更新敏感词库空状态标记
    }

    /**
     * 构建敏感词前缀树
     * 将单个敏感词插入前缀树，实现高效的多模式匹配基础
     * @param word 敏感词
     */
    public void buildSensitiveWordsTree(String word) {
        if (word == null || word.isEmpty()) {
            return; // 忽略空字符串
        }

        HashMap<Character, Tree> currentMap = sensitiveWordsTree; // 从根节点开始
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            Tree node = currentMap.get(c);

            if (node == null) {
                // 若当前字符对应的节点不存在，则创建新节点
                boolean isFinal = (i == word.length() - 1); // 最后一个字符标记为敏感词结尾
                node = new Tree(new HashMap<>(), isFinal);
                currentMap.put(c, node);
            } else {
                // 若节点已存在，更新结尾标记（处理更长的敏感词覆盖短敏感词的情况）
                if (i == word.length() - 1) {
                    node.setFinal(true);
                }
            }

            currentMap = node.getMap(); // 进入下一层节点
        }
    }

    /**
     * 检测单个分词中是否包含敏感词
     * 支持多字符敏感词匹配，返回所有匹配到的敏感词
     * @param word 单个分词
     * @return 该分词中包含的所有敏感词列表
     */
    public List<String> doSensitiveWordsFilter(String word) {
        List<String> result = new ArrayList<>();
        char[] chars = word.toCharArray();
        int length = chars.length;

        for (int i = 0; i < length; i++) {
            HashMap<Character, Tree> currentTree = sensitiveWordsTree;
            StringBuilder currentWord = new StringBuilder();
            int maxMatchEnd = -1; // 记录最长匹配的结束位置

            for (int j = i; j < length; j++) {
                char c = chars[j];
                if (!currentTree.containsKey(c)) {
                    break; // 字符不匹配，退出当前循环
                }

                Tree node = currentTree.get(c);
                currentWord.append(c); // 拼接当前字符

                // 若当前节点是敏感词结尾，记录完整敏感词
                if (node.isFinal()) {
                    result.add(currentWord.toString());
                    log.info("包含敏感词:{}", currentWord);
                    maxMatchEnd = j; // 更新最长匹配位置
                }

                currentTree = node.getMap(); // 继续检查下一层节点（更长的敏感词）
            }

            // 跳过已匹配的部分，避免重复检测
            if (maxMatchEnd != -1) {
                i = maxMatchEnd;
            }
        }

        return result;
    }

    /**
     * 统计前缀树中的敏感词总数
     * 递归遍历前缀树，累加所有标记为结尾的节点数量
     * @param node 前缀树节点（从根节点开始）
     * @return 敏感词总数
     */
    private int countSensitiveWords(HashMap<Character, Tree> node) {
        int count = 0;
        for (Tree tree : node.values()) {
            if (tree.isFinal()) {
                count++; // 累加当前节点作为结尾的敏感词
            }
            count += countSensitiveWords(tree.getMap()); // 递归统计子节点
        }
        return count;
    }
}