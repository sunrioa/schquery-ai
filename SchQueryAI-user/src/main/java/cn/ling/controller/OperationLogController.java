package cn.ling.controller;

import cn.ling.Result;
import cn.ling.domain.vo.OperationLogVO;
import cn.ling.service.OperationLogService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 操作日志 Controller
 */
@RestController
@RequestMapping("/user")
public class OperationLogController {

    @Resource
    private OperationLogService operationLogService;

    /**
     * 获取最近的操作日志
     */
    @GetMapping("/admin/recentLogs")
    public Result<List<OperationLogVO>> getRecentLogs(
            @RequestParam(required = false, defaultValue = "20") Integer limit) {
        return operationLogService.getRecentLogs(limit);
    }

    /**
     * 获取用户的操作日志
     */
    @GetMapping("/admin/userLogs")
    public Result<List<OperationLogVO>> getUserLogs(
            @RequestParam String operator,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return operationLogService.getUserLogs(operator, pageNum, pageSize);
    }

    /**
     * 清空操作日志
     */
    @PostMapping("/admin/clearLogs")
    public Result<String> clearLogs() {
        return operationLogService.clearLogs();
    }
}
