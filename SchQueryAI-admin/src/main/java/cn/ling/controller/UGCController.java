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
            // 从 ContextUtils 获取当前特散的用户名，如果没有则默认为admin
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

    @PostMapping("/sensitive/add")
    public Result<String> addSensitiveWords(@RequestBody List<String> words, HttpServletRequest request) {
        try {
            Result<String> result = ugcService.addSensitiveWords(words);
            String detail = words.size() > 0 ? String.format("新增敏感词: %s", String.join(", ", words)) : "新增敏感词：列表为空";
            if (result.getCode() == 200) {
                recordOperationLog(request, "添加敏感词", detail, true);
            } else {
                recordOperationLog(request, "添加敏感词", detail, false);
            }
            return result;
        } catch (Exception e) {
            String detail = words.size() > 0 ? String.format("新增敏感词失败: %s", String.join(", ", words)) : "新增敏感词失败";
            recordOperationLog(request, "添加敏感词", detail + "，原因: " + e.getMessage(), false);
            throw e;
        }
    }

    @DeleteMapping("/sensitive/delete")
    public Result<String> deleteSensitiveWords(@RequestBody List<Long> ids, HttpServletRequest request) {
        try {
            Result<String> result = ugcService.deleteSensitiveWords(ids);
            String detail = String.format("删除敏感词ID: %s", ids.toString());
            if (result.getCode() == 200) {
                recordOperationLog(request, "删除敏感词", detail, true);
            } else {
                recordOperationLog(request, "删除敏感词", detail, false);
            }
            return result;
        } catch (Exception e) {
            String detail = String.format("删除敏感词失败，ID: %s", ids.toString());
            recordOperationLog(request, "删除敏感词", detail + "，原因: " + e.getMessage(), false);
            throw e;
        }
    }

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
            if (result.getCode() == 200) {
                recordOperationLog(request, "修改敏感词", detail, true);
            } else {
                recordOperationLog(request, "修改敏感词", detail, false);
            }
            return result;
        } catch (Exception e) {
            recordOperationLog(request, "修改敏感词", "修改敏感词失败，原因: " + e.getMessage(), false);
            throw e;
        }
    }

    @GetMapping("/sensitive/query")
    public Result<IPage<SensitiveWordsVO>> querySensitiveWords(SensitiveWordsDTO sensitiveWordsDTO){
        return ugcService.querySensitiveWords(sensitiveWordsDTO);
    }

    @PostMapping("/segmentation/add")
    public Result<String> addSegmentationWords(@RequestBody List<String> words, HttpServletRequest request) {
        try {
            Result<String> result = ugcService.addSegmentationWords(words);
            String detail = words.size() > 0 ? String.format("新增分词: %s", String.join(", ", words)) : "新增分词：列表为空";
            if (result.getCode() == 200) {
                recordOperationLog(request, "添加分词", detail, true);
            } else {
                recordOperationLog(request, "添加分词", detail, false);
            }
            return result;
        } catch (Exception e) {
            String detail = words.size() > 0 ? String.format("新增分词失败: %s", String.join(", ", words)) : "新增分词失败";
            recordOperationLog(request, "添加分词", detail + "，原因: " + e.getMessage(), false);
            throw e;
        }
    }

    @DeleteMapping("/segmentation/delete")
    public Result<String> deleteSegmentationWords(@RequestBody List<Long> ids, HttpServletRequest request) {
        try {
            Result<String> result = ugcService.deleteSegmentationWords(ids);
            String detail = String.format("删除分词ID: %s", ids.toString());
            if (result.getCode() == 200) {
                recordOperationLog(request, "删除分词", detail, true);
            } else {
                recordOperationLog(request, "删除分词", detail, false);
            }
            return result;
        } catch (Exception e) {
            String detail = String.format("删除分词失败，ID: %s", ids.toString());
            recordOperationLog(request, "删除分词", detail + "，原因: " + e.getMessage(), false);
            throw e;
        }
    }

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
            if (result.getCode() == 200) {
                recordOperationLog(request, "修改分词", detail, true);
            } else {
                recordOperationLog(request, "修改分词", detail, false);
            }
            return result;
        } catch (Exception e) {
            recordOperationLog(request, "修改分词", "修改分词失败，原因: " + e.getMessage(), false);
            throw e;
        }
    }

    @GetMapping("/segmentation/query")
    public Result<IPage<SegmentationWordsVO>> querySegmentationWords(SegmentationWordsDTO segmentationWordsDTO){
        return ugcService.querySegmentationWords(segmentationWordsDTO);
    }

    @GetMapping("/sensitive/stats")
    public Result<Map<String, Object>> getSensitiveWordsStats() {
        return ugcService.getSensitiveWordsStats();
    }

    @GetMapping("/segmentation/stats")
    public Result<Map<String, Object>> getSegmentationWordsStats() {
        return ugcService.getSegmentationWordsStats();
    }

}