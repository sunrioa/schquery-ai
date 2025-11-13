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
import cn.ling.service.SensitiveWordsService;
import cn.ling.service.SegmentationWordsService;
import cn.ling.service.UGCService;
import cn.ling.utils.WordsFilterUtils;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
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
import java.util.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * UGC（用户生成内容）管理服务实现类
 * 实现敏感词和分词词库的综合管理功能，包括CRUD操作和过滤器实时更新
 * 支持敏感词和分词词汇的增删改查、批量操作、统计分析等功能
 */
@Slf4j
@Service
public class UGCServiceImpl implements UGCService {

    @Resource
    private SensitiveWordsMapper sensitiveWordsMapper;

    @Resource
    private SegmentationWordsMapper segmentationWordsMapper;

    @Resource
    private WordsFilterUtils wordsFilterUtils;

    @Resource
    private SensitiveWordsService sensitiveWordsService;

    @Resource
    private SegmentationWordsService segmentationWordsService;

    /**
     * 批量添加敏感词
     * 将新的敏感词添加到数据库中，并自动更新敏感词过滤器
     * 支持批量操作，提高敏感词库的维护效率
     *
     * @param words 待添加的敏感词列表
     * @return 添加操作的结果，包含成功或失败信息
     */
    @Override
    public Result<String> addSensitiveWords(List<String> words) {
        log.info("开始批量添加敏感词，输入数量: {}", words != null ? words.size() : 0);

        // 参数校验
        if (CollectionUtils.isEmpty(words)) {
            log.warn("敏感词列表为空");
            throw CustomException.error("敏感词列表不能为空");
        }

        List<SensitiveWords> sensitiveWordsList = new ArrayList<>();
        List<String> wordsToAddToTree = new ArrayList<>();
        int duplicateCount = 0;
        int emptyCount = 0;

        // 处理每个敏感词
        for (String word : words) {
            if (!StringUtils.hasText(word.trim())) {
                emptyCount++;
                continue;
            }

            String trimmedWord = word.trim();

            // 检查敏感词是否已存在
            if (sensitiveWordsService.lambdaQuery()
                        .eq(SensitiveWords::getWord, trimmedWord)
                        .one() != null) {
                log.warn("敏感词已存在，跳过：{}", trimmedWord);
                duplicateCount++;
                continue;
            }

            // 创建敏感词实体
            SensitiveWords sensitiveWords = SensitiveWords.builder()
                    .word(trimmedWord)
                    .status(1)
                    .createTime(new Date())
                    .updateTime(new Date())
                    .build();
            sensitiveWordsList.add(sensitiveWords);
            wordsToAddToTree.add(trimmedWord);
        }

        // 检查是否有新的敏感词需要添加
        if (sensitiveWordsList.isEmpty()) {
            log.warn("没有新的敏感词需要添加，重复: {}, 空值: {}", duplicateCount, emptyCount);
            throw CustomException.error("没有新的敏感词需要添加");
        }

        // 批量插入数据库
        int insertedCount = 0;
        for (SensitiveWords sensitiveWords : sensitiveWordsList) {
            if (sensitiveWordsMapper.insert(sensitiveWords) > 0) {
                insertedCount++;
            }
        }

        // 同步更新敏感词过滤器
        if (insertedCount > 0 && !wordsToAddToTree.isEmpty()) {
            wordsFilterUtils.enableSensitiveWords(wordsToAddToTree);
            log.info("成功将 {} 个敏感词启用到前缀树", wordsToAddToTree.size());
        }

        log.info("批量添加敏感词完成，成功: {}, 重复: {}, 空值: {}", insertedCount, duplicateCount, emptyCount);
        return Result.success("成功添加 " + insertedCount + " 个敏感词");
    }

    /**
     * 批量删除敏感词
     * 根据ID列表批量删除敏感词，同时更新敏感词过滤器
     * 用于敏感词库的批量清理和维护
     *
     * @param ids 待删除的敏感词ID列表
     * @return 删除操作的结果，包含成功或失败信息
     */
    @Override
    public Result<String> deleteSensitiveWords(List<Long> ids) {
        log.info("开始批量删除敏感词，ID数量: {}", ids != null ? ids.size() : 0);

        // 参数校验
        if (CollectionUtils.isEmpty(ids)) {
            log.warn("删除ID列表为空");
            throw CustomException.error("删除ID列表不能为空");
        }

        // 先查询要删除的敏感词，用于前缀树同步删除
        List<String> wordsToRemoveFromTree = new ArrayList<>();
        int notFoundCount = 0;

        for (Long id : ids) {
            SensitiveWords sensitiveWord = sensitiveWordsMapper.selectById(id);
            if (sensitiveWord != null) {
                wordsToRemoveFromTree.add(sensitiveWord.getWord());
            } else {
                notFoundCount++;
                log.warn("敏感词不存在，ID: {}", id);
            }
        }

        // 执行批量删除
        int deletedCount = 0;
        for (Long id : ids) {
            if (sensitiveWordsMapper.deleteById(id) > 0) {
                deletedCount++;
            }
        }

        // 同步更新敏感词过滤器
        if (deletedCount > 0 && !wordsToRemoveFromTree.isEmpty()) {
            wordsFilterUtils.disableSensitiveWords(wordsToRemoveFromTree);
            log.info("成功将 {} 个敏感词从前缀树禁用", wordsToRemoveFromTree.size());
        }

        log.info("批量删除敏感词完成，成功: {}, 不存在: {}", deletedCount, notFoundCount);
        return Result.success("成功删除 " + deletedCount + " 个敏感词");
    }

    /**
     * 批量更新敏感词
     * 根据DTO列表批量更新敏感词的信息（如内容、状态等）
     * 更新完成后会同步更新敏感词过滤器
     *
     * @param sensitiveWordsDTOList 待更新的敏感词DTO列表
     * @return 更新操作的结果，包含成功或失败信息
     */
    @Override
    public Result<String> updateSensitiveWords(List<SensitiveWordsDTO> sensitiveWordsDTOList) {
        log.info("开始批量更新敏感词，数量: {}", sensitiveWordsDTOList != null ? sensitiveWordsDTOList.size() : 0);

        // 参数校验
        if (CollectionUtils.isEmpty(sensitiveWordsDTOList)) {
            log.warn("更新敏感词列表为空");
            throw CustomException.error("更新敏感词列表不能为空");
        }

        int updatedCount = 0;
        int notFoundCount = 0;
        int contentChangedCount = 0;
        int statusChangedCount = 0;

        for (SensitiveWordsDTO dto : sensitiveWordsDTOList) {
            // 校验必要字段
            if (dto.getId() == null) {
                log.error("更新敏感词时ID不能为空");
                throw CustomException.error("更新敏感词时ID不能为空");
            }

            if (!StringUtils.hasText(dto.getWord())) {
                log.error("敏感词内容不能为空，ID: {}", dto.getId());
                throw CustomException.error("敏感词内容不能为空");
            }

            // 查询原始敏感词信息
            SensitiveWords originalSensitiveWord = sensitiveWordsMapper.selectById(dto.getId());
            if (originalSensitiveWord == null) {
                log.warn("敏感词不存在，跳过更新：ID={}", dto.getId());
                notFoundCount++;
                continue;
            }

            String newWord = dto.getWord().trim();
            String oldWord = originalSensitiveWord.getWord();
            Integer oldStatus = originalSensitiveWord.getStatus();
            Integer newStatus = dto.getStatus();

            // 构建更新条件
            var updateBuilder = sensitiveWordsService.lambdaUpdate()
                    .eq(SensitiveWords::getId, dto.getId());

            // 设置更新字段
            if (StringUtils.hasText(dto.getWord())) {
                updateBuilder.set(SensitiveWords::getWord, newWord);
            }

            if (newStatus != null && (newStatus == 0 || newStatus == 1)) {
                updateBuilder.set(SensitiveWords::getStatus, newStatus);
            }

            // 总是更新更新时间
            updateBuilder.set(SensitiveWords::getUpdateTime, new Date());

            // 执行更新
            if (updateBuilder.update()) {
                updatedCount++;

                // 同步更新前缀树
                if (!oldWord.equals(newWord)) {
                    // 词汇内容变化，先禁用旧词，再启用新词
                    wordsFilterUtils.disableSensitiveWord(oldWord);
                    if (newStatus != null && newStatus == 1) {
                        wordsFilterUtils.enableSensitiveWord(newWord);
                    }
                    log.info("敏感词前缀树更新：{} -> {}, 状态：{}", oldWord, newWord, newStatus);
                    contentChangedCount++;
                } else if (!Objects.equals(oldStatus, newStatus) && newStatus != null) {
                    // 词汇内容不变，但状态变化
                    if (newStatus == 1) {
                        wordsFilterUtils.enableSensitiveWord(newWord);
                        log.info("敏感词前缀树启用：{}", newWord);
                    } else {
                        wordsFilterUtils.disableSensitiveWord(newWord);
                        log.info("敏感词前缀树禁用：{}", newWord);
                    }
                    statusChangedCount++;
                }
            }
        }

        log.info("批量更新敏感词完成，成功: {}, 不存在: {}, 内容变化: {}, 状态变化: {}",
                updatedCount, notFoundCount, contentChangedCount, statusChangedCount);
        return Result.success("成功更新 " + updatedCount + " 个敏感词");
    }

    /**
     * 分页查询敏感词
     * 根据查询条件分页查询敏感词列表，支持按内容、状态等条件筛选
     * 用于敏感词管理界面的数据展示
     *
     * @param sensitiveWordsDTO 查询条件，包含分页参数和筛选条件
     * @return 分页查询结果，包含敏感词列表和分页信息
     */
    @Override
    public Result<IPage<SensitiveWordsVO>> querySensitiveWords(SensitiveWordsDTO sensitiveWordsDTO) {
        
        // 处理分页参数
        long current = sensitiveWordsDTO != null && sensitiveWordsDTO.getCurrent() != null ?
                sensitiveWordsDTO.getCurrent() : 1L;
        long size = sensitiveWordsDTO != null && sensitiveWordsDTO.getSize() != null ?
                sensitiveWordsDTO.getSize() : 10L;

        // 限制每页最大数量
        if (size > 100) {
            size = 100L;
            log.debug("限制每页最大数量为100，当前请求: {}", sensitiveWordsDTO.getSize());
        }

        // 执行分页查询
        IPage<SensitiveWords> pageResult = sensitiveWordsService.lambdaQuery()
                .eq(sensitiveWordsDTO != null && sensitiveWordsDTO.getId() != null, SensitiveWords::getId, sensitiveWordsDTO.getId())
                .like(StringUtils.hasText(sensitiveWordsDTO.getWord()), SensitiveWords::getWord, sensitiveWordsDTO.getWord())
                .eq(sensitiveWordsDTO.getStatus() != null, SensitiveWords::getStatus, sensitiveWordsDTO.getStatus())
                .orderByDesc(SensitiveWords::getId)
                .page(new Page<>(current, size));

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

        log.info("敏感词分页查询完成，总记录数: {}, 当前页记录数: {}",
                pageResult.getTotal(), sensitiveWordsVOList.size());
        return Result.success(voPage);
    }

    /**
     * 批量添加分词词
     * 将新的分词词汇添加到数据库中，并自动更新分词处理器
     * 用于扩充分词词库，提升文本分词的准确性
     *
     * @param words 待添加的分词词汇列表
     * @return 添加操作的结果，包含成功或失败信息
     */
    @Override
    public Result<String> addSegmentationWords(List<String> words) {
        log.info("开始批量添加分词词，输入数量: {}", words != null ? words.size() : 0);

        // 参数校验
        if (CollectionUtils.isEmpty(words)) {
            log.warn("分词列表为空");
            throw CustomException.error("分词列表不能为空");
        }

        List<SegmentationWords> segmentationWordsList = new ArrayList<>();
        int duplicateCount = 0;
        int emptyCount = 0;

        // 处理每个分词
        for (String word : words) {
            if (!StringUtils.hasText(word.trim())) {
                emptyCount++;
                continue;
            }

            String trimmedWord = word.trim();

            // 检查分词是否已存在
            if (segmentationWordsService.lambdaQuery()
                        .eq(SegmentationWords::getWord, trimmedWord)
                        .one() != null) {
                log.warn("分词已存在，跳过：{}", trimmedWord);
                duplicateCount++;
                continue;
            }

            // 创建分词实体
            SegmentationWords segmentationWords = SegmentationWords.builder()
                    .word(trimmedWord)
                    .status(1)
                    .createTime(new Date())
                    .updateTime(new Date())
                    .build();
            segmentationWordsList.add(segmentationWords);
        }

        // 检查是否有新的分词需要添加
        if (segmentationWordsList.isEmpty()) {
            log.warn("没有新的分词需要添加，重复: {}, 空值: {}", duplicateCount, emptyCount);
            throw CustomException.error("没有新的分词需要添加");
        }

        // 批量插入数据库
        int insertedCount = 0;
        for (SegmentationWords segmentationWords : segmentationWordsList) {
            if (segmentationWordsMapper.insert(segmentationWords) > 0) {
                insertedCount++;
            }
        }

        log.info("批量添加分词完成，成功: {}, 重复: {}, 空值: {}", insertedCount, duplicateCount, emptyCount);
        return Result.success("成功添加 " + insertedCount + " 个分词");
    }

    /**
     * 批量删除分词词
     * 根据ID列表批量删除分词词汇，同时更新分词处理器
     * 用于分词词库的清理和维护
     *
     * @param ids 待删除的分词词汇ID列表
     * @return 删除操作的结果，包含成功或失败信息
     */
    @Override
    public Result<String> deleteSegmentationWords(List<Long> ids) {
        log.info("开始批量删除分词词，ID数量: {}", ids != null ? ids.size() : 0);

        // 参数校验
        if (CollectionUtils.isEmpty(ids)) {
            log.warn("删除ID列表为空");
            throw CustomException.error("删除ID列表不能为空");
        }

        int deletedCount = 0;
        int notFoundCount = 0;

        for (Long id : ids) {
            if (segmentationWordsMapper.selectById(id) == null) {
                notFoundCount++;
                log.warn("分词不存在，ID: {}", id);
                continue;
            }

            if (segmentationWordsMapper.deleteById(id) > 0) {
                deletedCount++;
            }
        }

        log.info("批量删除分词完成，成功: {}, 不存在: {}", deletedCount, notFoundCount);
        return Result.success("成功删除 " + deletedCount + " 个分词");
    }

    /**
     * 批量更新分词词
     * 根据DTO列表批量更新分词词汇的信息（如内容、状态等）
     * 更新完成后会同步更新分词处理器
     *
     * @param segmentationWordsDTOList 待更新的分词词汇DTO列表
     * @return 更新操作的结果，包含成功或失败信息
     */
    @Override
    public Result<String> updateSegmentationWords(List<SegmentationWordsDTO> segmentationWordsDTOList) {
        log.info("开始批量更新分词词，数量: {}", segmentationWordsDTOList != null ? segmentationWordsDTOList.size() : 0);

        // 参数校验
        if (CollectionUtils.isEmpty(segmentationWordsDTOList)) {
            log.warn("更新分词列表为空");
            throw CustomException.error("更新分词列表不能为空");
        }

        int updatedCount = 0;
        int notFoundCount = 0;

        for (SegmentationWordsDTO dto : segmentationWordsDTOList) {
            // 校验必要字段
            if (dto.getId() == null) {
                log.error("更新分词时ID不能为空");
                throw CustomException.error("更新分词时ID不能为空");
            }

            if (!StringUtils.hasText(dto.getWord())) {
                log.error("分词内容不能为空，ID: {}", dto.getId());
                throw CustomException.error("分词内容不能为空");
            }

            // 检查分词是否存在
            if (segmentationWordsMapper.selectById(dto.getId()) == null) {
                log.warn("分词不存在，跳过更新：ID={}", dto.getId());
                notFoundCount++;
                continue;
            }

            // 构建更新条件
            var updateBuilder = segmentationWordsService.lambdaUpdate()
                    .eq(SegmentationWords::getId, dto.getId());

            // 设置更新字段
            if (StringUtils.hasText(dto.getWord())) {
                updateBuilder.set(SegmentationWords::getWord, dto.getWord().trim());
            }

            if (dto.getStatus() != null && (dto.getStatus() == 0 || dto.getStatus() == 1)) {
                updateBuilder.set(SegmentationWords::getStatus, dto.getStatus());
            }

            // 总是更新更新时间
            updateBuilder.set(SegmentationWords::getUpdateTime, new Date());

            // 执行更新
            if (updateBuilder.update()) {
                updatedCount++;
            }
        }

        log.info("批量更新分词完成，成功: {}, 不存在: {}", updatedCount, notFoundCount);
        return Result.success("成功更新 " + updatedCount + " 个分词");
    }

    /**
     * 分页查询分词词
     * 根据查询条件分页查询分词词汇列表，支持按内容、状态等条件筛选
     * 用于分词词库管理界面的数据展示
     *
     * @param segmentationWordsDTO 查询条件，包含分页参数和筛选条件
     * @return 分页查询结果，包含分词词汇列表和分页信息
     */
    @Override
    public Result<IPage<SegmentationWordsVO>> querySegmentationWords(SegmentationWordsDTO segmentationWordsDTO) {
        
        // 处理分页参数
        long current = segmentationWordsDTO != null && segmentationWordsDTO.getCurrent() != null ?
                segmentationWordsDTO.getCurrent() : 1L;
        long size = segmentationWordsDTO != null && segmentationWordsDTO.getSize() != null ?
                segmentationWordsDTO.getSize() : 10L;

        // 限制每页最大数量
        if (size > 100) {
            size = 100L;
            log.debug("限制每页最大数量为100，当前请求: {}", segmentationWordsDTO.getSize());
        }

        // 执行分页查询
        IPage<SegmentationWords> pageResult = segmentationWordsService.lambdaQuery()
                .eq(segmentationWordsDTO != null && segmentationWordsDTO.getId() != null, SegmentationWords::getId, segmentationWordsDTO.getId())
                .like(StringUtils.hasText(segmentationWordsDTO.getWord()), SegmentationWords::getWord, segmentationWordsDTO.getWord())
                .eq(segmentationWordsDTO.getStatus() != null, SegmentationWords::getStatus, segmentationWordsDTO.getStatus())
                .orderByDesc(SegmentationWords::getId)
                .page(new Page<>(current, size));

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

        log.info("分词分页查询完成，总记录数: {}, 当前页记录数: {}",
                pageResult.getTotal(), segmentationWordsVOList.size());
        return Result.success(voPage);
    }

    /**
     * 获取敏感词统计数据
     * 统计敏感词库的各种数据指标，包括总数量、启用/禁用数量、今日新增等
     * 用于管理后台的数据展示和分析
     *
     * @return 敏感词统计数据的映射，包含各种统计指标
     */
    @Override
    public Result<Map<String, Object>> getSensitiveWordsStats() {
        try {
            // 查询统计数据
            LocalDate today = LocalDate.now();
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

            Date todayStart = Date.from(startOfDay.atZone(ZoneId.systemDefault()).toInstant());
            Date todayEnd = Date.from(endOfDay.atZone(ZoneId.systemDefault()).toInstant());

            Long totalCount = sensitiveWordsService.lambdaQuery().count();
            Long enabledCount = sensitiveWordsService.lambdaQuery().eq(SensitiveWords::getStatus, 1).count();
            Long disabledCount = sensitiveWordsService.lambdaQuery().eq(SensitiveWords::getStatus, 0).count();
            Long todayNewCount = sensitiveWordsService.lambdaQuery()
                    .between(SensitiveWords::getCreateTime, todayStart, todayEnd)
                    .count();

            // 构建统计结果
            Map<String, Object> stats = new HashMap<>();
            stats.put("total", totalCount != null ? totalCount : 0L);
            stats.put("enabled", enabledCount != null ? enabledCount : 0L);
            stats.put("disabled", disabledCount != null ? disabledCount : 0L);
            stats.put("todayNew", todayNewCount != null ? todayNewCount : 0L);

            log.info("敏感词统计数据获取完成 - 总数: {}, 启用: {}, 禁用: {}, 今日新增: {}",
                    stats.get("total"), stats.get("enabled"), stats.get("disabled"), stats.get("todayNew"));

            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取敏感词统计数据失败", e);
            throw CustomException.error("获取敏感词统计数据失败：" + e.getMessage());
        }
    }

    /**
     * 获取分词词统计数据
     * 统计分词词库的各种数据指标，包括总数量、启用/禁用数量、今日新增等
     * 用于管理后台的数据展示和分析
     *
     * @return 分词词统计数据的映射，包含各种统计指标
     */
    @Override
    public Result<Map<String, Object>> getSegmentationWordsStats() {
        try {
            // 查询统计数据
            LocalDate today = LocalDate.now();
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

            Date todayStart = Date.from(startOfDay.atZone(ZoneId.systemDefault()).toInstant());
            Date todayEnd = Date.from(endOfDay.atZone(ZoneId.systemDefault()).toInstant());

            Long totalCount = segmentationWordsService.lambdaQuery().count();
            Long enabledCount = segmentationWordsService.lambdaQuery().eq(SegmentationWords::getStatus, 1).count();
            Long disabledCount = segmentationWordsService.lambdaQuery().eq(SegmentationWords::getStatus, 0).count();
            Long todayNewCount = segmentationWordsService.lambdaQuery()
                    .between(SegmentationWords::getCreateTime, todayStart, todayEnd)
                    .count();

            // 构建统计结果
            Map<String, Object> stats = new HashMap<>();
            stats.put("total", totalCount != null ? totalCount : 0L);
            stats.put("enabled", enabledCount != null ? enabledCount : 0L);
            stats.put("disabled", disabledCount != null ? disabledCount : 0L);
            stats.put("todayNew", todayNewCount != null ? todayNewCount : 0L);

            log.info("分词词统计数据获取完成 - 总数: {}, 启用: {}, 禁用: {}, 今日新增: {}",
                    stats.get("total"), stats.get("enabled"), stats.get("disabled"), stats.get("todayNew"));

            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取分词统计数据失败", e);
            throw CustomException.error("获取分词统计数据失败：" + e.getMessage());
        }
    }
}
