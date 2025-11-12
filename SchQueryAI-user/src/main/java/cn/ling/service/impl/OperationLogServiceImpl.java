package cn.ling.service.impl;

import cn.ling.Result;
import cn.ling.domain.pojo.OperationLog;
import cn.ling.domain.vo.OperationLogVO;
import cn.ling.mapper.OperationLogMapper;
import cn.ling.service.OperationLogService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 操作日志服务实现
 */
@Slf4j
@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog>
        implements OperationLogService {

    /**
     * 获取客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "未知";
        }

        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }

        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        ip = request.getHeader("Proxy-Client-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        ip = request.getHeader("WL-Proxy-Client-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        ip = request.getRemoteAddr();
        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            return "本地";
        }

        return ip;
    }

    @Override
    public void recordLog(String operator, String action, String detail, Integer status, HttpServletRequest request) {
        try {
            String ip = getClientIp(request);
            String userAgent = request != null ? request.getHeader("User-Agent") : null;

            OperationLog operationLog = OperationLog.builder()
                    .operator(operator)
                    .action(action)
                    .detail(detail)
                    .status(status != null ? status : 1)
                    .ipAddress(ip)
                    .userAgent(userAgent)
                    .createTime(new Date())
                    .build();

            save(operationLog);
            log.info("操作日志记录成功 - 操作人: {}, 操作: {}, 状态: {}", operator, action, status);
        } catch (Exception e) {
            log.error("记录操作日志失败", e);
        }
    }

    @Override
    public Result<List<OperationLogVO>> getRecentLogs(Integer limit) {
        try {
            if (limit == null || limit < 1) {
                limit = 10;
            }
            if (limit > 100) {
                limit = 100;
            }

            Page<OperationLog> page = new Page<>(1, limit);
            IPage<OperationLog> result = lambdaQuery()
                    .orderByDesc(OperationLog::getCreateTime)
                    .page(page);

            List<OperationLogVO> voList = result.getRecords().stream()
                    .map(this::convertToVO)
                    .collect(Collectors.toList());

            return Result.success(voList);
        } catch (Exception e) {
            log.error("获取最近操作日志失败", e);
            return Result.error("获取操作日志失败");
        }
    }

    @Override
    public Result<List<OperationLogVO>> getUserLogs(String operator, Integer pageNum, Integer pageSize) {
        try {
            if (pageNum == null || pageNum < 1) {
                pageNum = 1;
            }
            if (pageSize == null || pageSize < 1) {
                pageSize = 10;
            }
            if (pageSize > 100) {
                pageSize = 100;
            }

            Page<OperationLog> page = new Page<>(pageNum, pageSize);
            IPage<OperationLog> result = lambdaQuery()
                    .eq(OperationLog::getOperator, operator)
                    .orderByDesc(OperationLog::getCreateTime)
                    .page(page);

            List<OperationLogVO> voList = result.getRecords().stream()
                    .map(this::convertToVO)
                    .collect(Collectors.toList());

            return Result.success(voList);
        } catch (Exception e) {
            log.error("获取用户操作日志失败", e);
            return Result.error("获取操作日志失败");
        }
    }

    @Override
    public Result<String> clearLogs() {
        try {
            remove(null);
            log.info("操作日志已清空");
            return Result.success("日志已清空");
        } catch (Exception e) {
            log.error("清空操作日志失败", e);
            return Result.error("清空日志失败");
        }
    }

    /**
     * 转换为VO对象
     */
    private OperationLogVO convertToVO(OperationLog operationLog) {
        return OperationLogVO.builder()
                .id(operationLog.getId())
                .operator(operationLog.getOperator())
                .action(operationLog.getAction())
                .detail(operationLog.getDetail())
                .status(operationLog.getStatus())
                .ipAddress(operationLog.getIpAddress())
                .userAgent(operationLog.getUserAgent())
                .timestamp(operationLog.getCreateTime())
                .build();
    }
}
