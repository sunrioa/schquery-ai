package cn.ling.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
    public List<String> doFilter(String comment) {
        // 边界处理：空文本或无敏感词库时直接返回成功
        if (comment == null || comment.isEmpty()) {
            return List.of();
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

        return sensitiveWords.stream().toList();
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
        private ConcurrentHashMap<Character, Tree> map; // 子节点映射（字符到节点的映射）
        private boolean isFinal; // 标记当前节点是否为某个敏感词的结尾
    }

    private static final ConcurrentHashMap<Character, Tree> sensitiveWordsTree = new ConcurrentHashMap<>(); // 敏感词前缀树根节点

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

        ConcurrentHashMap<Character, Tree> currentMap = sensitiveWordsTree; // 从根节点开始
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            Tree node = currentMap.get(c);

            if (node == null) {
                // 若当前字符对应的节点不存在，则创建新节点
                boolean isFinal = (i == word.length() - 1); // 最后一个字符标记为敏感词结尾
                node = new Tree(new ConcurrentHashMap<>(), isFinal);
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
            ConcurrentHashMap<Character, Tree> currentTree = sensitiveWordsTree;
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
    private int countSensitiveWords(ConcurrentHashMap<Character, Tree> node) {
        int count = 0;
        for (Tree tree : node.values()) {
            if (tree.isFinal()) {
                count++; // 累加当前节点作为结尾的敏感词
            }
            count += countSensitiveWords(tree.getMap()); // 递归统计子节点
        }
        return count;
    }

    /**
     * 启用敏感词（在前缀树中标记为敏感词结尾）
     * 通过设置isFinal=true来启用敏感词检测
     * @param word 待启用的敏感词
     */
    public void enableSensitiveWord(String word) {
        if (word == null || word.trim().isEmpty()) {
            return;
        }

        word = word.trim();
        Tree node = findWordNode(word);
        if (node != null) {
            node.setFinal(true);
            isEmpty = false;
            log.info("启用敏感词：{}", word);
        } else {
            // 如果节点不存在，需要构建完整的节点路径
            buildSensitiveWordsTree(word);
            log.info("添加并启用敏感词：{}", word);
        }
    }

    /**
     * 禁用敏感词（在前缀树中取消标记为敏感词结尾）
     * 通过设置isFinal=false来禁用敏感词检测，但保留节点结构
     * @param word 待禁用的敏感词
     */
    public void disableSensitiveWord(String word) {
        if (word == null || word.trim().isEmpty()) {
            return;
        }

        word = word.trim();
        Tree node = findWordNode(word);
        if (node != null && node.isFinal()) {
            node.setFinal(false);
            log.info("禁用敏感词：{}", word);

            // 检查是否还有其他启用的敏感词
            int wordCount = countSensitiveWords(sensitiveWordsTree);
            isEmpty = wordCount == 0;
        }
    }

    /**
     * 更新敏感词（禁用旧词，启用新词）
     * @param oldWord 原敏感词
     * @param newWord 新敏感词
     */
    public void updateSensitiveWord(String oldWord, String newWord) {
        if (oldWord == null || newWord == null || oldWord.trim().isEmpty() || newWord.trim().isEmpty()) {
            return;
        }

        oldWord = oldWord.trim();
        newWord = newWord.trim();

        // 禁用旧敏感词
        disableSensitiveWord(oldWord);

        // 启用新敏感词
        enableSensitiveWord(newWord);

        log.info("更新敏感词：{} -> {}", oldWord, newWord);
    }

    /**
     * 批量启用敏感词
     * @param words 待启用的敏感词列表
     */
    public void enableSensitiveWords(List<String> words) {
        if (CollectionUtils.isEmpty(words)) {
            return;
        }

        int enabledCount = 0;
        for (String word : words) {
            if (word != null && !word.trim().isEmpty()) {
                String trimmedWord = word.trim();
                Tree node = findWordNode(trimmedWord);
                if (node == null) {
                    buildSensitiveWordsTree(trimmedWord);
                    enabledCount++;
                } else if (!node.isFinal()) {
                    node.setFinal(true);
                    enabledCount++;
                }
            }
        }

        if (enabledCount > 0) {
            isEmpty = false;
            log.info("批量启用敏感词，启用数量：{}", enabledCount);
        }
    }

    /**
     * 批量禁用敏感词
     * @param words 待禁用的敏感词列表
     */
    public void disableSensitiveWords(List<String> words) {
        if (CollectionUtils.isEmpty(words)) {
            return;
        }

        int disabledCount = 0;
        for (String word : words) {
            if (word != null && !word.trim().isEmpty()) {
                String trimmedWord = word.trim();
                Tree node = findWordNode(trimmedWord);
                if (node != null && node.isFinal()) {
                    node.setFinal(false);
                    disabledCount++;
                }
            }
        }

        if (disabledCount > 0) {
            int wordCount = countSensitiveWords(sensitiveWordsTree);
            isEmpty = wordCount == 0;
            log.info("批量禁用敏感词，禁用数量：{}，剩余数量：{}", disabledCount, wordCount);
        }
    }

    /**
     * 查找敏感词在前缀树中的节点
     * @param word 待查找的敏感词
     * @return 敏感词对应的节点，不存在则返回null
     */
    private Tree findWordNode(String word) {
        if (word == null || word.isEmpty()) {
            return null;
        }

        ConcurrentHashMap<Character, Tree> currentMap = sensitiveWordsTree;
        Tree node = null;

        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            node = currentMap.get(c);
            if (node == null) {
                return null;
            }
            if (i == word.length() - 1) {
                return node; // 返回最后一个字符对应的节点
            }
            currentMap = node.getMap();
        }

        return node;
    }

    /**
     * 检查敏感词是否存在于前缀树中并且处于启用状态
     * @param word 待检查的敏感词
     * @return true-存在且启用，false-不存在或禁用
     */
    public boolean containsSensitiveWord(String word) {
        if (word == null || word.isEmpty() || isEmpty) {
            return false;
        }

        ConcurrentHashMap<Character, Tree> currentMap = sensitiveWordsTree;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            Tree node = currentMap.get(c);
            if (node == null) {
                return false;
            }

            // 如果是最后一个字符，检查是否为敏感词结尾
            if (i == word.length() - 1) {
                return node.isFinal();
            }

            currentMap = node.getMap();
        }

        return false;
    }

    /**
     * 重新构建整个前缀树
     * 用于大批量操作时的性能优化
     * @param words 新的敏感词列表
     */
    public void rebuildTree(List<String> words) {
        // 清空现有前缀树
        sensitiveWordsTree.clear();

        // 重新构建前缀树
        if (!CollectionUtils.isEmpty(words)) {
            for (String word : words) {
                if (word != null && !word.trim().isEmpty()) {
                    buildSensitiveWordsTree(word.trim());
                }
            }
        }

        int wordCount = countSensitiveWords(sensitiveWordsTree);
        isEmpty = wordCount == 0;
        log.info("重新构建敏感词前缀树，敏感词数量：{}", wordCount);
    }

    /**
     * 获取前缀树中所有启用状态的敏感词
     * 用于调试和管理
     * @return 所有启用的敏感词列表
     */
    public List<String> getAllSensitiveWords() {
        List<String> words = new ArrayList<>();
        collectWords(sensitiveWordsTree, new StringBuilder(), words);
        return words;
    }

    /**
     * 递归收集前缀树中的所有启用状态的敏感词
     * @param node 当前节点
     * @param currentWord 当前构建的单词
     * @param words 结果列表
     */
    private void collectWords(ConcurrentHashMap<Character, Tree> node, StringBuilder currentWord, List<String> words) {
        for (Map.Entry<Character, Tree> entry : node.entrySet()) {
            char c = entry.getKey();
            Tree tree = entry.getValue();

            currentWord.append(c);

            if (tree.isFinal()) {
                words.add(currentWord.toString());
            }

            if (!tree.getMap().isEmpty()) {
                collectWords(tree.getMap(), currentWord, words);
            }

            currentWord.deleteCharAt(currentWord.length() - 1);
        }
    }
}