package cn.ling.controller;

import cn.ling.Result;
import cn.ling.domain.dto.SegmentationWordsDTO;
import cn.ling.domain.dto.SensitiveWordsDTO;
import cn.ling.domain.vo.SegmentationWordsVO;
import cn.ling.domain.vo.SensitiveWordsVO;
import cn.ling.service.UGCService;
import cn.ling.service.OperationLogService;
import cn.ling.utils.ContextUtils;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/**
 * UGC（用户生成内容）管理控制器
 * 提供敏感词和分词词库的管理接口，用于内容审核和过滤系统的维护
 * 支持敏感词和分词词汇的增删改查、批量导入、状态管理等功能
 */
@Slf4j
@RestController
@RequestMapping("/admin/UGC")
public class UGCController {

    @Resource
    private UGCService ugcService;

    @Resource
    private OperationLogService operationLogService;

    /**
     * 记录操作日志的辅助方法
     */
    private void recordOperationLog(HttpServletRequest request, String action, String detail, boolean success) {
        try {
            // 从 ContextUtils 获取当前登录的用户名，如果没有则默认为admin
            String username = "admin";
            try {
                String contextUsername = ContextUtils.getUsername();
                if (contextUsername != null && !contextUsername.isEmpty()) {
                    username = contextUsername;
                }
            } catch (Exception e) {
                // 日志记录：从 Context 获取用户名失败，使用默认值
                log.debug("从 ContextUtils 获取用户名失败，使用默认值: {}", e.getMessage());
            }
            // 直接调用 OperationLogService 来保存日志
            operationLogService.recordLog(username, action, detail, success ? 1 : 0, request);
            log.info("操作日志记录成功 - 操作人: {}, 操作: {}, 状态: {}", username, action, success ? "成功" : "失败");
        } catch (Exception e) {
            // 日志记录失败不影响业务流程，只打印警告
            log.warn("记录操作日志失败: {}", e.getMessage());
        }
    }

    /**
     * 批量添加敏感词
     * 接收敏感词列表并批量添加到数据库中，同时更新敏感词过滤器
     *
     * @param words 待添加的敏感词列表
     * @param request HTTP请求对象，用于记录操作日志
     * @return 添加操作的结果
     */
    @PostMapping("/sensitive/add")
    public Result<String> addSensitiveWords(@RequestBody List<String> words, HttpServletRequest request) {
        try {
            Result<String> result = ugcService.addSensitiveWords(words);
            String detail = !words.isEmpty() ? String.format("新增敏感词: %s", String.join(", ", words)) : "新增敏感词：列表为空";
            recordOperationLog(request, "添加敏感词", detail, result.getCode() == 200);
            return result;
        } catch (Exception e) {
            String detail = !words.isEmpty() ? String.format("新增敏感词失败: %s", String.join(", ", words)) : "新增敏感词失败";
            recordOperationLog(request, "添加敏感词", detail + "，原因: " + e.getMessage(), false);
            throw e;
        }
    }

    /**
     * 批量删除敏感词
     * 根据ID列表批量删除敏感词，同时更新敏感词过滤器
     *
     * @param ids 待删除的敏感词ID列表
     * @param request HTTP请求对象，用于记录操作日志
     * @return 删除操作的结果
     */
    @DeleteMapping("/sensitive/delete")
    public Result<String> deleteSensitiveWords(@RequestBody List<Long> ids, HttpServletRequest request) {
        try {
            Result<String> result = ugcService.deleteSensitiveWords(ids);
            String detail = String.format("删除敏感词ID: %s", ids.toString());
            recordOperationLog(request, "删除敏感词", detail, result.getCode() == 200);
            return result;
        } catch (Exception e) {
            String detail = String.format("删除敏感词失败，ID: %s", ids.toString());
            recordOperationLog(request, "删除敏感词", detail + "，原因: " + e.getMessage(), false);
            throw e;
        }
    }

    /**
     * 批量更新敏感词
     * 根据DTO列表批量更新敏感词的信息（如内容、状态等），同时更新敏感词过滤器
     *
     * @param sensitiveWordsDTOList 待更新的敏感词DTO列表
     * @param request HTTP请求对象，用于记录操作日志
     * @return 更新操作的结果
     */
    @PutMapping("/sensitive/update")
    public Result<String> updateSensitiveWords(@RequestBody List<SensitiveWordsDTO> sensitiveWordsDTOList, HttpServletRequest request) {
        try {
            // 构建详细的修改内容
            StringBuilder detailBuilder = new StringBuilder("修改敏感词: ");
            for (int i = 0; i < sensitiveWordsDTOList.size(); i++) {
                SensitiveWordsDTO dto = sensitiveWordsDTOList.get(i);
                if (i > 0) detailBuilder.append("; ");
                detailBuilder.append(String.format("ID:%d → %s", dto.getId(), dto.getWord()));
            }
            String detail = detailBuilder.toString();

            Result<String> result = ugcService.updateSensitiveWords(sensitiveWordsDTOList);
            recordOperationLog(request, "修改敏感词", detail, result.getCode() == 200);
            return result;
        } catch (Exception e) {
            recordOperationLog(request, "修改敏感词", "修改敏感词失败，原因: " + e.getMessage(), false);
            throw e;
        }
    }

    /**
     * 分页查询敏感词
     * 根据查询条件分页查询敏感词列表，支持按内容、状态等条件筛选
     *
     * @param sensitiveWordsDTO 查询条件，包含分页参数和筛选条件
     * @return 分页查询结果，包含敏感词列表和分页信息
     */
    @GetMapping("/sensitive/query")
    public Result<IPage<SensitiveWordsVO>> querySensitiveWords(SensitiveWordsDTO sensitiveWordsDTO){
        return ugcService.querySensitiveWords(sensitiveWordsDTO);
    }

    /**
     * 批量添加分词词
     * 接收分词词汇列表并批量添加到数据库中，同时更新分词处理器
     *
     * @param words 待添加的分词词汇列表
     * @param request HTTP请求对象，用于记录操作日志
     * @return 添加操作的结果
     */
    @PostMapping("/segmentation/add")
    public Result<String> addSegmentationWords(@RequestBody List<String> words, HttpServletRequest request) {
        try {
            Result<String> result = ugcService.addSegmentationWords(words);
            String detail = !words.isEmpty() ? String.format("新增分词: %s", String.join(", ", words)) : "新增分词：列表为空";
            recordOperationLog(request, "添加分词", detail, result.getCode() == 200);
            return result;
        } catch (Exception e) {
            String detail = !words.isEmpty() ? String.format("新增分词失败: %s", String.join(", ", words)) : "新增分词失败";
            recordOperationLog(request, "添加分词", detail + "，原因: " + e.getMessage(), false);
            throw e;
        }
    }

    /**
     * 批量删除分词词
     * 根据ID列表批量删除分词词汇，同时更新分词处理器
     *
     * @param ids 待删除的分词词汇ID列表
     * @param request HTTP请求对象，用于记录操作日志
     * @return 删除操作的结果
     */
    @DeleteMapping("/segmentation/delete")
    public Result<String> deleteSegmentationWords(@RequestBody List<Long> ids, HttpServletRequest request) {
        try {
            Result<String> result = ugcService.deleteSegmentationWords(ids);
            String detail = String.format("删除分词ID: %s", ids.toString());
            recordOperationLog(request, "删除分词", detail, result.getCode() == 200);
            return result;
        } catch (Exception e) {
            String detail = String.format("删除分词失败，ID: %s", ids.toString());
            recordOperationLog(request, "删除分词", detail + "，原因: " + e.getMessage(), false);
            throw e;
        }
    }

    /**
     * 批量更新分词词
     * 根据DTO列表批量更新分词词汇的信息（如内容、状态等），同时更新分词处理器
     *
     * @param segmentationWordsDTOList 待更新的分词词汇DTO列表
     * @param request HTTP请求对象，用于记录操作日志
     * @return 更新操作的结果
     */
    @PutMapping("/segmentation/update")
    public Result<String> updateSegmentationWords(@RequestBody List<SegmentationWordsDTO> segmentationWordsDTOList, HttpServletRequest request) {
        try {
            // 构建详细的修改内容
            StringBuilder detailBuilder = new StringBuilder("修改分词: ");
            for (int i = 0; i < segmentationWordsDTOList.size(); i++) {
                SegmentationWordsDTO dto = segmentationWordsDTOList.get(i);
                if (i > 0) detailBuilder.append("; ");
                detailBuilder.append(String.format("ID:%d → %s", dto.getId(), dto.getWord()));
            }
            String detail = detailBuilder.toString();

            Result<String> result = ugcService.updateSegmentationWords(segmentationWordsDTOList);
            recordOperationLog(request, "修改分词", detail, result.getCode() == 200);
            return result;
        } catch (Exception e) {
            recordOperationLog(request, "修改分词", "修改分词失败，原因: " + e.getMessage(), false);
            throw e;
        }
    }

    /**
     * 分页查询分词词
     * 根据查询条件分页查询分词词汇列表，支持按内容、状态等条件筛选
     *
     * @param segmentationWordsDTO 查询条件，包含分页参数和筛选条件
     * @return 分页查询结果，包含分词词汇列表和分页信息
     */
    @GetMapping("/segmentation/query")
    public Result<IPage<SegmentationWordsVO>> querySegmentationWords(SegmentationWordsDTO segmentationWordsDTO){
        return ugcService.querySegmentationWords(segmentationWordsDTO);
    }

    /**
     * 获取敏感词统计数据
     * 统计敏感词库的各种数据指标，包括总数量、启用/禁用数量、今日新增等
     * 用于管理后台的数据展示和分析
     *
     * @return 敏感词统计数据的映射，包含各种统计指标
     */
    @GetMapping("/sensitive/stats")
    public Result<Map<String, Object>> getSensitiveWordsStats() {
        return ugcService.getSensitiveWordsStats();
    }

    /**
     * 获取分词词统计数据
     * 统计分词词库的各种数据指标，包括总数量、启用/禁用数量、今日新增等
     * 用于管理后台的数据展示和分析
     *
     * @return 分词词统计数据的映射，包含各种统计指标
     */
    @GetMapping("/segmentation/stats")
    public Result<Map<String, Object>> getSegmentationWordsStats() {
        return ugcService.getSegmentationWordsStats();
    }

}