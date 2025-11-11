package cn.ling.service.impl;

import cn.ling.Result;
import cn.ling.domain.dto.SegmentationWordsDTO;
import cn.ling.domain.dto.SensitiveWordsDTO;
import cn.ling.domain.pojo.SegmentationWords;
import cn.ling.domain.pojo.SensitiveWords;
import cn.ling.domain.vo.SegmentationWordsVO;
import cn.ling.domain.vo.SensitiveWordsVO;
import cn.ling.exception.CustomException;
import cn.ling.mapper.SegmentationWordsMapper;
import cn.ling.mapper.SensitiveWordsMapper;
import cn.ling.service.UGCService;
import cn.ling.utils.WordsFilterUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UGCServiceImpl implements UGCService {

    @Resource
    private SensitiveWordsMapper sensitiveWordsMapper;

    @Resource
    private SegmentationWordsMapper segmentationWordsMapper;

    @Resource
    private WordsFilterUtils wordsFilterUtils;
    @Override
    public Result<String> addSensitiveWords(List<String> words) {
        try {
            if (CollectionUtils.isEmpty(words)) {
                throw CustomException.error("敏感词列表不能为空");
            }

            List<SensitiveWords> sensitiveWordsList = new ArrayList<>();
            List<String> wordsToAddToTree = new ArrayList<>();

            for (String word : words) {
                if (!StringUtils.hasText(word.trim())) {
                    continue;
                }

                String trimmedWord = word.trim();

                LambdaQueryWrapper<SensitiveWords> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(SensitiveWords::getWord, trimmedWord);
                if (sensitiveWordsMapper.selectOne(queryWrapper) != null) {
                    log.warn("敏感词已存在，跳过：{}", trimmedWord);
                    continue;
                }

                SensitiveWords sensitiveWords = SensitiveWords.builder()
                        .word(trimmedWord)
                        .status(1)
                        .build();
                sensitiveWordsList.add(sensitiveWords);
                wordsToAddToTree.add(trimmedWord);
            }

            if (sensitiveWordsList.isEmpty()) {
                return Result.error("没有新的敏感词需要添加");
            }

            int insertedCount = 0;
            for (SensitiveWords sensitiveWords : sensitiveWordsList) {
                if (sensitiveWordsMapper.insert(sensitiveWords) > 0) {
                    insertedCount++;
                }
            }

            // 同步更新前缀树
            if (insertedCount > 0 && !wordsToAddToTree.isEmpty()) {
                wordsFilterUtils.enableSensitiveWords(wordsToAddToTree);
                log.info("成功将 {} 个敏感词启用到前缀树", wordsToAddToTree.size());
            }

            return Result.success("成功添加 " + insertedCount + " 个敏感词");
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("添加敏感词失败", e);
            throw CustomException.error("添加敏感词失败：" + e.getMessage());
        }
    }

    @Override
    public Result<String> deleteSensitiveWords(List<Long> ids) {
        try {
            if (CollectionUtils.isEmpty(ids)) {
                throw CustomException.error("删除ID列表不能为空");
            }

            // 先查询要删除的敏感词，用于前缀树同步删除
            List<String> wordsToRemoveFromTree = new ArrayList<>();
            for (Long id : ids) {
                SensitiveWords sensitiveWord = sensitiveWordsMapper.selectById(id);
                if (sensitiveWord != null) {
                    wordsToRemoveFromTree.add(sensitiveWord.getWord());
                }
            }

            int deletedCount = 0;
            for (Long id : ids) {
                if (sensitiveWordsMapper.deleteById(id) > 0) {
                    deletedCount++;
                }
            }

            // 同步更新前缀树
            if (deletedCount > 0 && !wordsToRemoveFromTree.isEmpty()) {
                wordsFilterUtils.disableSensitiveWords(wordsToRemoveFromTree);
                log.info("成功将 {} 个敏感词从前缀树禁用", wordsToRemoveFromTree.size());
            }

            return Result.success("成功删除 " + deletedCount + " 个敏感词");
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除敏感词失败", e);
            throw CustomException.error("删除敏感词失败：" + e.getMessage());
        }
    }

    @Override
    public Result<String> updateSensitiveWords(List<SensitiveWordsDTO> sensitiveWordsDTOList) {
        try {
            if (CollectionUtils.isEmpty(sensitiveWordsDTOList)) {
                throw CustomException.error("更新敏感词列表不能为空");
            }

            int updatedCount = 0;
            for (SensitiveWordsDTO dto : sensitiveWordsDTOList) {
                if (dto.getId() == null) {
                    throw CustomException.error("更新敏感词时ID不能为空");
                }

                if (!StringUtils.hasText(dto.getWord())) {
                    throw CustomException.error("敏感词内容不能为空");
                }

                // 先查询原始敏感词信息，用于前缀树同步更新
                SensitiveWords originalSensitiveWord = sensitiveWordsMapper.selectById(dto.getId());
                if (originalSensitiveWord == null) {
                    log.warn("敏感词不存在，跳过更新：ID={}", dto.getId());
                    continue;
                }

                String newWord = dto.getWord().trim();
                String oldWord = originalSensitiveWord.getWord();
                Integer oldStatus = originalSensitiveWord.getStatus();
                Integer newStatus = dto.getStatus();

                LambdaUpdateWrapper<SensitiveWords> updateWrapper = new LambdaUpdateWrapper<>();
                updateWrapper.eq(SensitiveWords::getId, dto.getId());

                if (StringUtils.hasText(dto.getWord())) {
                    updateWrapper.set(SensitiveWords::getWord, newWord);
                }

                if (newStatus != null && (newStatus == 0 || newStatus == 1)) {
                    updateWrapper.set(SensitiveWords::getStatus, newStatus);
                }

                if (sensitiveWordsMapper.update(null, updateWrapper) > 0) {
                    updatedCount++;

                    // 同步更新前缀树
                    if (!oldWord.equals(newWord)) {
                        // 词汇内容变化，先禁用旧词，再启用新词
                        wordsFilterUtils.disableSensitiveWord(oldWord);
                        if (newStatus != null && newStatus == 1) {
                            wordsFilterUtils.enableSensitiveWord(newWord);
                        }
                        log.info("敏感词前缀树更新：{} -> {}, 状态：{}", oldWord, newWord, newStatus);
                    } else if (!Objects.equals(oldStatus, newStatus) && newStatus != null) {
                        // 词汇内容不变，但状态变化
                        if (newStatus == 1) {
                            wordsFilterUtils.enableSensitiveWord(newWord);
                            log.info("敏感词前缀树启用：{}", newWord);
                        } else {
                            wordsFilterUtils.disableSensitiveWord(newWord);
                            log.info("敏感词前缀树禁用：{}", newWord);
                        }
                    }
                }
            }

            return Result.success("成功更新 " + updatedCount + " 个敏感词");
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("更新敏感词失败", e);
            throw CustomException.error("更新敏感词失败：" + e.getMessage());
        }
    }

    @Override
    public Result<IPage<SensitiveWordsVO>> querySensitiveWords(SensitiveWordsDTO sensitiveWordsDTO) {
        try {
            // 处理分页参数
            long current = sensitiveWordsDTO != null && sensitiveWordsDTO.getCurrent() != null ?
                    sensitiveWordsDTO.getCurrent() : 1L;
            long size = sensitiveWordsDTO != null && sensitiveWordsDTO.getSize() != null ?
                    sensitiveWordsDTO.getSize() : 10L;

            // 限制每页最大数量
            if (size > 100) {
                size = 100L;
            }

            // 构建查询条件
            LambdaQueryWrapper<SensitiveWords> queryWrapper = new LambdaQueryWrapper<>();

            if (sensitiveWordsDTO != null) {
                if (sensitiveWordsDTO.getId() != null) {
                    queryWrapper.eq(SensitiveWords::getId, sensitiveWordsDTO.getId());
                }

                if (StringUtils.hasText(sensitiveWordsDTO.getWord())) {
                    queryWrapper.like(SensitiveWords::getWord, sensitiveWordsDTO.getWord().trim());
                }

                if (sensitiveWordsDTO.getStatus() != null && (sensitiveWordsDTO.getStatus() == 0 || sensitiveWordsDTO.getStatus() == 1)) {
                    queryWrapper.eq(SensitiveWords::getStatus, sensitiveWordsDTO.getStatus());
                }
            }

            queryWrapper.orderByDesc(SensitiveWords::getId);

            // 执行分页查询
            IPage<SensitiveWords> pageResult = sensitiveWordsMapper.selectPage(new Page<>(current, size), queryWrapper);

            // 转换为VO并构建新的IPage对象
            IPage<SensitiveWordsVO> voPage = new Page<>(current, size, pageResult.getTotal());
            List<SensitiveWordsVO> sensitiveWordsVOList = pageResult.getRecords().stream()
                    .map(sw -> {
                        SensitiveWordsVO vo = new SensitiveWordsVO();
                        vo.setId(sw.getId());
                        vo.setWord(sw.getWord());
                        vo.setStatus(sw.getStatus());
                        return vo;
                    })
                    .collect(Collectors.toList());

            voPage.setRecords(sensitiveWordsVOList);

            return Result.success(voPage);
        } catch (Exception e) {
            log.error("查询敏感词失败", e);
            throw CustomException.error("查询敏感词失败：" + e.getMessage());
        }
    }

    @Override
    public Result<String> addSegmentationWords(List<String> words) {
        try {
            if (CollectionUtils.isEmpty(words)) {
                throw CustomException.error("分词列表不能为空");
            }

            List<SegmentationWords> segmentationWordsList = new ArrayList<>();
            for (String word : words) {
                if (!StringUtils.hasText(word.trim())) {
                    continue;
                }

                String trimmedWord = word.trim();

                LambdaQueryWrapper<SegmentationWords> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(SegmentationWords::getWord, trimmedWord);
                if (segmentationWordsMapper.selectOne(queryWrapper) != null) {
                    log.warn("分词已存在，跳过：{}", trimmedWord);
                    continue;
                }

                SegmentationWords segmentationWords = SegmentationWords.builder()
                        .word(trimmedWord)
                        .status(1)
                        .build();
                segmentationWordsList.add(segmentationWords);
            }

            if (segmentationWordsList.isEmpty()) {
                return Result.error("没有新的分词需要添加");
            }

            int insertedCount = 0;
            for (SegmentationWords segmentationWords : segmentationWordsList) {
                if (segmentationWordsMapper.insert(segmentationWords) > 0) {
                    insertedCount++;
                }
            }

            return Result.success("成功添加 " + insertedCount + " 个分词");
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("添加分词失败", e);
            throw CustomException.error("添加分词失败：" + e.getMessage());
        }
    }

    @Override
    public Result<String> deleteSegmentationWords(List<Long> ids) {
        try {
            if (CollectionUtils.isEmpty(ids)) {
                throw CustomException.error("删除ID列表不能为空");
            }

            int deletedCount = 0;
            for (Long id : ids) {
                if (segmentationWordsMapper.deleteById(id) > 0) {
                    deletedCount++;
                }
            }

            return Result.success("成功删除 " + deletedCount + " 个分词");
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除分词失败", e);
            throw CustomException.error("删除分词失败：" + e.getMessage());
        }
    }

    @Override
    public Result<String> updateSegmentationWords(List<SegmentationWordsDTO> segmentationWordsDTOList) {
        try {
            if (CollectionUtils.isEmpty(segmentationWordsDTOList)) {
                throw CustomException.error("更新分词列表不能为空");
            }

            int updatedCount = 0;
            for (SegmentationWordsDTO dto : segmentationWordsDTOList) {
                if (dto.getId() == null) {
                    throw CustomException.error("更新分词时ID不能为空");
                }

                if (!StringUtils.hasText(dto.getWord())) {
                    throw CustomException.error("分词内容不能为空");
                }

                LambdaUpdateWrapper<SegmentationWords> updateWrapper = new LambdaUpdateWrapper<>();
                updateWrapper.eq(SegmentationWords::getId, dto.getId());

                if (StringUtils.hasText(dto.getWord())) {
                    updateWrapper.set(SegmentationWords::getWord, dto.getWord().trim());
                }

                if (dto.getStatus() != null && (dto.getStatus() == 0 || dto.getStatus() == 1)) {
                    updateWrapper.set(SegmentationWords::getStatus, dto.getStatus());
                }

                if (segmentationWordsMapper.update(null, updateWrapper) > 0) {
                    updatedCount++;
                }
            }

            return Result.success("成功更新 " + updatedCount + " 个分词");
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("更新分词失败", e);
            throw CustomException.error("更新分词失败：" + e.getMessage());
        }
    }

    @Override
    public Result<IPage<SegmentationWordsVO>> querySegmentationWords(SegmentationWordsDTO segmentationWordsDTO) {
        try {
            // 处理分页参数
            long current = segmentationWordsDTO != null && segmentationWordsDTO.getCurrent() != null ?
                    segmentationWordsDTO.getCurrent() : 1L;
            long size = segmentationWordsDTO != null && segmentationWordsDTO.getSize() != null ?
                    segmentationWordsDTO.getSize() : 10L;

            // 限制每页最大数量
            if (size > 100) {
                size = 100L;
            }

            // 构建查询条件
            LambdaQueryWrapper<SegmentationWords> queryWrapper = new LambdaQueryWrapper<>();

            if (segmentationWordsDTO != null) {
                if (segmentationWordsDTO.getId() != null) {
                    queryWrapper.eq(SegmentationWords::getId, segmentationWordsDTO.getId());
                }

                if (StringUtils.hasText(segmentationWordsDTO.getWord())) {
                    queryWrapper.like(SegmentationWords::getWord, segmentationWordsDTO.getWord().trim());
                }

                if (segmentationWordsDTO.getStatus() != null && (segmentationWordsDTO.getStatus() == 0 || segmentationWordsDTO.getStatus() == 1)) {
                    queryWrapper.eq(SegmentationWords::getStatus, segmentationWordsDTO.getStatus());
                }
            }

            queryWrapper.orderByDesc(SegmentationWords::getId);

            // 执行分页查询
            IPage<SegmentationWords> pageResult = segmentationWordsMapper.selectPage(new Page<>(current, size), queryWrapper);

            // 转换为VO并构建新的IPage对象
            IPage<SegmentationWordsVO> voPage = new Page<>(current, size, pageResult.getTotal());
            List<SegmentationWordsVO> segmentationWordsVOList = pageResult.getRecords().stream()
                    .map(sw -> {
                        SegmentationWordsVO vo = new SegmentationWordsVO();
                        vo.setId(sw.getId());
                        vo.setWord(sw.getWord());
                        vo.setStatus(sw.getStatus());
                        return vo;
                    })
                    .collect(Collectors.toList());

            voPage.setRecords(segmentationWordsVOList);

            return Result.success(voPage);
        } catch (Exception e) {
            log.error("查询分词失败", e);
            throw CustomException.error("查询分词失败：" + e.getMessage());
        }
    }

    @Override
    public Result<Map<String, Object>> getSensitiveWordsStats() {
        try {
            // 查询总记录数
            LambdaQueryWrapper<SensitiveWords> queryWrapper = new LambdaQueryWrapper<>();
            Long totalCount = sensitiveWordsMapper.selectCount(queryWrapper);

            // 查询启用状态的记录数
            LambdaQueryWrapper<SensitiveWords> enabledWrapper = new LambdaQueryWrapper<>();
            enabledWrapper.eq(SensitiveWords::getStatus, 1);
            Long enabledCount = sensitiveWordsMapper.selectCount(enabledWrapper);

            // 查询禁用状态的记录数
            LambdaQueryWrapper<SensitiveWords> disabledWrapper = new LambdaQueryWrapper<>();
            disabledWrapper.eq(SensitiveWords::getStatus, 0);
            Long disabledCount = sensitiveWordsMapper.selectCount(disabledWrapper);

            // 构建统计结果
            Map<String, Object> stats = new HashMap<>();
            stats.put("total", totalCount != null ? totalCount : 0L);
            stats.put("enabled", enabledCount != null ? enabledCount : 0L);
            stats.put("disabled", disabledCount != null ? disabledCount : 0L);

            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取敏感词统计数据失败", e);
            throw CustomException.error("获取敏感词统计数据失败：" + e.getMessage());
        }
    }

    @Override
    public Result<Map<String, Object>> getSegmentationWordsStats() {
        try {
            // 查询总记录数
            LambdaQueryWrapper<SegmentationWords> queryWrapper = new LambdaQueryWrapper<>();
            Long totalCount = segmentationWordsMapper.selectCount(queryWrapper);

            // 查询启用状态的记录数
            LambdaQueryWrapper<SegmentationWords> enabledWrapper = new LambdaQueryWrapper<>();
            enabledWrapper.eq(SegmentationWords::getStatus, 1);
            Long enabledCount = segmentationWordsMapper.selectCount(enabledWrapper);

            // 查询禁用状态的记录数
            LambdaQueryWrapper<SegmentationWords> disabledWrapper = new LambdaQueryWrapper<>();
            disabledWrapper.eq(SegmentationWords::getStatus, 0);
            Long disabledCount = segmentationWordsMapper.selectCount(disabledWrapper);

            // 构建统计结果
            Map<String, Object> stats = new HashMap<>();
            stats.put("total", totalCount != null ? totalCount : 0L);
            stats.put("enabled", enabledCount != null ? enabledCount : 0L);
            stats.put("disabled", disabledCount != null ? disabledCount : 0L);

            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取分词统计数据失败", e);
            throw CustomException.error("获取分词统计数据失败：" + e.getMessage());
        }
    }
}
