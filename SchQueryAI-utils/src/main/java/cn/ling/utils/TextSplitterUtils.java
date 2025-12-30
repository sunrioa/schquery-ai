package cn.ling.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

public class TextSplitterUtils {
    private final List<String> separators;
    private final int chunkSize;
    private final int chunkOverlap;
    private final LengthFunction lengthFunction;

    public interface LengthFunction {
        int calculate(String text);
    }

    public TextSplitterUtils(
            List<String> separators,
            int chunkSize,
            int chunkOverlap,
            LengthFunction lengthFunction) {
        this.separators = separators;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.lengthFunction = lengthFunction;
    }

    public List<String> getSeparators() {
        return new ArrayList<>(separators);
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public LengthFunction getLengthFunction() {
        return lengthFunction;
    }

    public List<String> splitText(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }

        // 先清理文本，移除多余的空白字符
        String cleanedText = cleanText(text);

        // 使用递归方式拆分文本
        splitTextRecursive(cleanedText, chunks, 0);
        return chunks;
    }

    private void splitTextRecursive(String text, List<String> chunks, int depth) {
        // 防止递归过深
        if (depth > 1000) {
            System.err.println("Warning: Maximum recursion depth reached");
            // 强制分割剩余文本
            forceSplitText(text, chunks);
            return;
        }

        int textLength = lengthFunction.calculate(text);

        // 终止条件：文本长度小于等于chunkSize，直接加入结果
        if (textLength <= chunkSize) {
            if (isValidChunk(text)) {
                chunks.add(text);
            }
            return;
        }

        // 优先按分隔符分层拆分
        boolean hasValidSplit = false;
        for (String sep : separators) {
            if (sep.isEmpty()) continue; // 跳过空分隔符

            if (text.contains(sep)) {
                List<String> parts = splitBySeparator(text, sep);

                // 处理分割后的每个部分
                boolean splitSuccess = false;
                for (String part : parts) {
                    String cleanedPart = cleanText(part);
                    if (cleanedPart.isEmpty() || cleanedPart.equals(text)) {
                        continue; // 跳过空片段和未分割的文本
                    }

                    int partLength = lengthFunction.calculate(cleanedPart);
                    if (partLength > chunkSize) {
                        // 超长时递归切割
                        splitTextRecursive(cleanedPart, chunks, depth + 1);
                        splitSuccess = true;
                    } else if (partLength > 0 && isValidChunk(cleanedPart)) {
                        // 长度合适的直接加入结果
                        chunks.add(cleanedPart);
                        splitSuccess = true;
                    }
                }

                if (splitSuccess) {
                    hasValidSplit = true;
                    break; // 使用第一个有效分隔符就停止
                }
            }
        }

        // 如果所有分隔符都不满足或无法有效分割，强制按chunkSize切割（保留重叠）
        if (!hasValidSplit) {
            forceSplitText(text, chunks);
        }
    }

    // 强制文本分割（当无法使用分隔符时）
    private void forceSplitText(String text, List<String> chunks) {
        int textLength = lengthFunction.calculate(text);

        while (textLength > chunkSize) {
            // 使用智能分割点
            String currentChunk = findSemanticSplitPoint(text, chunkSize);

            if (currentChunk.length() == 0) {
                break; // 防止无限循环
            }

            if (isValidChunk(currentChunk)) {
                chunks.add(currentChunk);
            }

            // 计算剩余文本的起始位置（保留重叠）
            int splitPos = currentChunk.length();
            int remainingStart = splitPos - chunkOverlap;
            if (remainingStart <= 0) {
                remainingStart = splitPos / 2; // 至少前进一半
            }

            // 确保剩余文本比当前文本更短
            if (remainingStart >= text.length()) {
                break; // 防止无限循环
            }

            String remainingText = text.substring(remainingStart);
            int remainingLength = lengthFunction.calculate(remainingText);

            // 如果剩余文本仍然过长，递归处理
            if (remainingLength > chunkSize) {
                forceSplitText(remainingText, chunks);
            } else if (remainingLength > 0 && isValidChunk(remainingText)) {
                chunks.add(remainingText);
            }

            break; // 只处理一次，避免重复处理
        }
    }

    // 清理文本，移除多余的空白字符
    private String cleanText(String text) {
        if (text == null) return "";
        return text.trim().replaceAll("\\s+", " ").replaceAll("[\\r\\n]+", "\n");
    }

    // 检查是否为有效的chunk
    private boolean isValidChunk(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        String trimmed = text.trim();
        if (trimmed.length() < 20) {
            return false; // 提高最小长度要求，确保语义完整性
        }

        // 检查是否包含有意义的字符（不只是空白和单个标点）
        boolean hasValidContent = false;
        for (char c : trimmed.toCharArray()) {
            if (Character.isLetterOrDigit(c) || Character.isIdeographic(c)) {
                hasValidContent = true;
                break;
            }
        }

        return hasValidContent;
    }

    // 智能分割：尝试在语义边界分割，优先保障语义单元完整性
    private String findSemanticSplitPoint(String text, int targetLength) {
        if (text.length() <= targetLength) {
            return text;
        }

        // 优先级1：寻找模块/章节边界（如"教育背景"、"实习经历"等）
        int bestSplit = findModuleBoundary(text, targetLength);
        if (bestSplit != -1) {
            return text.substring(0, bestSplit);
        }

        // 优先级2：寻找列表项边界（如"1. "、"2. "等）
        bestSplit = findListItemBoundary(text, targetLength);
        if (bestSplit != -1) {
            return text.substring(0, bestSplit);
        }

        // 优先级3：寻找段落边界（双换行）
        bestSplit = text.lastIndexOf("\n\n", targetLength);
        if (bestSplit > targetLength / 2) {
            return text.substring(0, bestSplit);
        }

        // 优先级4：寻找句子结束点
        String[] sentenceEnders = {"。", "！", "？", "；"};
        for (String ender : sentenceEnders) {
            int index = text.lastIndexOf(ender, targetLength);
            if (index > targetLength / 2) {
                return text.substring(0, index + 1);
            }
        }

        // 优先级5：寻找逗号
        int commaIndex = text.lastIndexOf("，", targetLength);
        if (commaIndex > targetLength / 2) {
            return text.substring(0, commaIndex + 1);
        }

        // 兜底：在目标长度处分割
        return text.substring(0, targetLength);
    }

    // 寻找模块/章节边界 - 针对招生信息查询模块优化
    private int findModuleBoundary(String text, int targetLength) {
        // 招生相关的关键词
        String[] moduleKeywords = {
                // 招生信息模块
                "招生信息查询", "专业信息查询", "招生计划查询", "历年分数线查询",
                "招生政策查询", "报考指南查询", "校园信息查询",
                // 通用章节标题
                "一、", "二、", "三、", "四、", "五、", "六、", "七、", "八、", "九、", "十、",
                "（一）", "（二）", "（三）", "（四）", "（五）", "（六）", "（七）", "（八）", "（九）", "（十）",
                "1. ", "2. ", "3. ", "4. ", "5. ", "6. ", "7. ", "8. ", "9. ", "10. ",
                // 学校相关关键词
                "专业设置", "培养方案", "课程体系", "就业前景", "招生计划", "录取分数线",
                "招生简章", "报考条件", "录取规则", "加分政策", "志愿填报", "报考流程",
                "校园环境", "基础设施", "学生生活", "学校概况", "院系介绍", "师资力量",
                // 章节标题模式
                "第.*章", "第.*条", "第.*节", "第.*款", "第.*项",
                // 其他常见分割点
                "发布时间", "供稿", "编辑", "来源", "作者", "日期"
        };

        for (String keyword : moduleKeywords) {
            int index = -1;

            // 对于正则表达式模式，使用find方法
            if (keyword.matches("第.*章|第.*条|第.*节|第.*款|第.*项")) {
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(keyword);
                java.util.regex.Matcher matcher = pattern.matcher(text);
                int foundIndex = -1;
                while (matcher.find()) {
                    int matchIndex = matcher.start();
                    if (matchIndex <= targetLength && matchIndex > foundIndex) {
                        foundIndex = matchIndex;
                    }
                }
                index = foundIndex;
            } else {
                index = text.lastIndexOf(keyword, targetLength);
            }

            if (index > targetLength / 2) {
                // 查找关键词后的换行符作为分割点
                int newLineIndex = text.indexOf("\n", index);
                if (newLineIndex != -1 && newLineIndex < targetLength + 50) {
                    return newLineIndex + 1;
                }
                // 如果没有找到换行符，直接返回关键词后位置
                if (newLineIndex == -1 && index + keyword.length() <= targetLength + 50) {
                    return index + keyword.length();
                }
            }
        }
        return -1;
    }

    // 寻找列表项边界
    private int findListItemBoundary(String text, int targetLength) {
        // 寻找下一个列表项的开始位置作为分割点
        for (int i = 1; i <= 9; i++) {
            String listItem = i + ". ";
            int index = text.indexOf(listItem, targetLength - 20);
            if (index > targetLength / 2 && index < targetLength + 100) {
                return index;
            }
        }
        return -1;
    }

    private List<String> splitBySeparator(String text, String sep) {
        List<String> parts = new ArrayList<>();

        // 处理空分隔符的特殊情况
        if (sep.isEmpty()) {
            // 对于空分隔符，直接返回整个文本，避免无限递归
            parts.add(text);
            return parts;
        }

        // 检查是否为正则表达式分隔符
        if (isRegexSeparator(sep)) {
            return splitByRegex(text, sep);
        }

        // 普通字符串分隔符处理
        int start = 0;
        int index;
        while ((index = text.indexOf(sep, start)) != -1) {
            parts.add(text.substring(start, index + sep.length()));
            start = index + sep.length();
            // 防止无限循环：如果start位置没有变化，直接跳出
            if (start <= index) {
                break;
            }
        }
        if (start < text.length()) {
            parts.add(text.substring(start));
        }
        return parts;
    }

    // 检查是否为正则表达式分隔符
    private boolean isRegexSeparator(String sep) {
        return sep.matches("第.*章|第.*条|第.*节|第.*款|第.*项");
    }

    // 使用正则表达式分割文本
    private List<String> splitByRegex(String text, String regex) {
        List<String> parts = new ArrayList<>();
        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
            java.util.regex.Matcher matcher = pattern.matcher(text);

            int lastEnd = 0;
            while (matcher.find()) {
                if (matcher.start() > lastEnd) {
                    // 添加匹配前的内容
                    parts.add(text.substring(lastEnd, matcher.start()));
                }
                // 添加匹配的内容
                parts.add(text.substring(matcher.start(), matcher.end()));
                lastEnd = matcher.end();
            }

            // 添加剩余内容
            if (lastEnd < text.length()) {
                parts.add(text.substring(lastEnd));
            }
        } catch (Exception e) {
            // 如果正则表达式出错，返回原文本
            parts.add(text);
        }

        return parts;
    }

    public static LengthFunction defaultLengthFunction() {
        return String::length;
    }

    public static class Builder {
        private List<String> separators = Arrays.asList("\n\n", "\n", "。", "；", "！", "？", " ", "1. ", "2. ", "3. ", "4. ", "5. ", "6. ", "7. ", "8. ", "9. ");
        private int chunkSize = 1000;
        private int chunkOverlap = 100;
        private LengthFunction lengthFunction = defaultLengthFunction();

        public Builder separators(List<String> separators) {
            this.separators = separators;
            return this;
        }

        public Builder chunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
            return this;
        }

        public Builder chunkOverlap(int chunkOverlap) {
            this.chunkOverlap = chunkOverlap;
            return this;
        }

        public Builder lengthFunction(LengthFunction lengthFunction) {
            this.lengthFunction = lengthFunction;
            return this;
        }

        public TextSplitterUtils build() {
            return new TextSplitterUtils(separators, chunkSize, chunkOverlap, lengthFunction);
        }
    }
}
