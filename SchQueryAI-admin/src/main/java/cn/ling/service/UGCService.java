package cn.ling.service;

import cn.ling.Result;
import cn.ling.domain.dto.SegmentationWordsDTO;
import cn.ling.domain.dto.SensitiveWordsDTO;
import cn.ling.domain.vo.SegmentationWordsVO;
import cn.ling.domain.vo.SensitiveWordsVO;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.Map;

/**
 * UGC（用户生成内容）管理服务接口
 * 提供敏感词和分词词库的综合管理功能，用于内容审核和过滤系统的维护
 * 支持敏感词和分词词汇的增删改查、批量操作、统计分析等功能
 * <p>
 * 主要功能模块：
 * 1. 敏感词管理 - 敏感词的添加、删除、修改、查询和统计分析
 * 2. 分词词库管理 - 分词词汇的添加、删除、修改、查询和统计分析
 * 3. 过滤器更新 - 实时更新内存中的敏感词过滤器和分词处理器
 */
public interface UGCService {

    // ========== 敏感词管理相关方法 ==========

    /**
     * 批量添加敏感词
     * 将新的敏感词添加到数据库中，并自动更新敏感词过滤器
     * 支持批量操作，提高敏感词库的维护效率
     *
     * @param words 待添加的敏感词列表
     * @return 添加操作的结果，包含成功或失败信息
     */
    Result<String> addSensitiveWords(List<String> words);

    /**
     * 批量删除敏感词
     * 根据ID列表批量删除敏感词，同时更新敏感词过滤器
     * 用于敏感词库的批量清理和维护
     *
     * @param ids 待删除的敏感词ID列表
     * @return 删除操作的结果，包含成功或失败信息
     */
    Result<String> deleteSensitiveWords(List<Long> ids);

    /**
     * 批量更新敏感词
     * 根据DTO列表批量更新敏感词的信息（如内容、状态等）
     * 更新完成后会同步更新敏感词过滤器
     *
     * @param sensitiveWordsDTOList 待更新的敏感词DTO列表
     * @return 更新操作的结果，包含成功或失败信息
     */
    Result<String> updateSensitiveWords(List<SensitiveWordsDTO> sensitiveWordsDTOList);

    /**
     * 分页查询敏感词
     * 根据查询条件分页查询敏感词列表，支持按内容、状态等条件筛选
     * 用于敏感词管理界面的数据展示
     *
     * @param sensitiveWordsDTO 查询条件，包含分页参数和筛选条件
     * @return 分页查询结果，包含敏感词列表和分页信息
     */
    Result<IPage<SensitiveWordsVO>> querySensitiveWords(SensitiveWordsDTO sensitiveWordsDTO);

    // ========== 分词词库管理相关方法 ==========

    /**
     * 批量添加分词词
     * 将新的分词词汇添加到数据库中，并自动更新分词处理器
     * 用于扩充分词词库，提升文本分词的准确性
     *
     * @param words 待添加的分词词汇列表
     * @return 添加操作的结果，包含成功或失败信息
     */
    Result<String> addSegmentationWords(List<String> words);

    /**
     * 批量删除分词词
     * 根据ID列表批量删除分词词汇，同时更新分词处理器
     * 用于分词词库的清理和维护
     *
     * @param ids 待删除的分词词汇ID列表
     * @return 删除操作的结果，包含成功或失败信息
     */
    Result<String> deleteSegmentationWords(List<Long> ids);

    /**
     * 批量更新分词词
     * 根据DTO列表批量更新分词词汇的信息（如内容、状态等）
     * 更新完成后会同步更新分词处理器
     *
     * @param segmentationWordsDTOList 待更新的分词词汇DTO列表
     * @return 更新操作的结果，包含成功或失败信息
     */
    Result<String> updateSegmentationWords(List<SegmentationWordsDTO> segmentationWordsDTOList);

    /**
     * 分页查询分词词
     * 根据查询条件分页查询分词词汇列表，支持按内容、状态等条件筛选
     * 用于分词词库管理界面的数据展示
     *
     * @param segmentationWordsDTO 查询条件，包含分页参数和筛选条件
     * @return 分页查询结果，包含分词词汇列表和分页信息
     */
    Result<IPage<SegmentationWordsVO>> querySegmentationWords(SegmentationWordsDTO segmentationWordsDTO);

    // ========== 统计分析相关方法 ==========

    /**
     * 获取敏感词统计数据
     * 统计敏感词库的各种数据指标，用于管理后台的数据展示
     * 包括总数量、启用/禁用数量统计等
     *
     * @return 敏感词统计数据的映射，包含各种统计指标
     */
    Result<Map<String, Object>> getSensitiveWordsStats();

    /**
     * 获取分词词统计数据
     * 统计分词词库的各种数据指标，用于管理后台的数据展示
     * 包括总数量、启用/禁用数量统计等
     *
     * @return 分词词统计数据的映射，包含各种统计指标
     */
    Result<Map<String, Object>> getSegmentationWordsStats();
}
