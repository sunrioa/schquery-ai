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
 * 操作日志服务实现类
 * 记录和管理系统中的用户操作行为，包括增删改查、系统配置等关键操作的审计日志
 * 提供操作日志的记录、查询、分页展示和清空功能，用于系统安全审计和行为追踪
 */
@Slf4j
@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog>
        implements OperationLogService {

    /**
     * 获取客户端真实IP地址
     * 处理各种代理和负载均衡情况，优先获取X-Forwarded-For头中的真实IP
     *
     * @param request HTTP请求对象
     * @return 客户端真实IP地址，无法获取时返回"未知"
     */
    private String getClientIp(HttpServletRequest request) {
        log.debug("开始获取客户端IP地址");

        if (request == null) {
            log.warn("HTTP请求对象为null，无法获取客户端IP");
            return "未知";
        }

        try {
            // 检查X-Forwarded-For头（通常用于代理服务器）
            String ip = request.getHeader("X-Forwarded-For");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                log.debug("从X-Forwarded-For头获取到IP: {}", ip);
                // X-Forwarded-For可能包含多个IP，取第一个
                return ip.split(",")[0].trim();
            }

            // 检查X-Real-IP头（nginx常用）
            ip = request.getHeader("X-Real-IP");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                log.debug("从X-Real-IP头获取到IP: {}", ip);
                return ip;
            }

            // 检查Proxy-Client-IP头（Apache常用）
            ip = request.getHeader("Proxy-Client-IP");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                log.debug("从Proxy-Client-IP头获取到IP: {}", ip);
                return ip;
            }

            // 检查WL-Proxy-Client-IP头（WebLogic常用）
            ip = request.getHeader("WL-Proxy-Client-IP");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                log.debug("从WL-Proxy-Client-IP头获取到IP: {}", ip);
                return ip;
            }

            // 直接获取RemoteAddr
            ip = request.getRemoteAddr();
            if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
                log.debug("检测到本地访问，返回'本地'");
                return "本地";
            }

            log.debug("从RemoteAddr获取到IP: {}", ip);
            return ip;
        } catch (Exception e) {
            log.error("获取客户端IP地址时发生异常: {}", e.getMessage(), e);
            return "未知";
        }
    }

    /**
     * 记录操作日志
     * 保存用户的系统操作行为，包括操作人、操作类型、详细信息、IP地址等
     *
     * @param operator 操作人标识（用户名或用户ID）
     * @param action 操作类型（如：登录、删除、修改等）
     * @param detail 操作详细信息
     * @param status 操作状态（1：成功，0：失败）
     * @param request HTTP请求对象，用于获取IP和User-Agent信息
     */
    @Override
    public void recordLog(String operator, String action, String detail, Integer status, HttpServletRequest request) {
        log.info("开始记录操作日志 - 操作人: {}, 操作: {}, 详情: {}, 状态: {}",
            operator, action, detail, status);

        try {
            // 获取客户端IP地址
            String ip = getClientIp(request);
            log.debug("获取到客户端IP: {}", ip);

            // 获取User-Agent信息
            String userAgent = request != null ? request.getHeader("User-Agent") : null;
            log.debug("获取到User-Agent: {}", userAgent != null && userAgent.length() > 100 ?
                userAgent.substring(0, 100) + "..." : userAgent);

            // 创建操作日志对象
            OperationLog operationLog = OperationLog.builder()
                    .operator(operator)
                    .action(action)
                    .detail(detail)
                    .status(status != null ? status : 1) // 默认为成功状态
                    .ipAddress(ip)
                    .userAgent(userAgent)
                    .createTime(new Date())
                    .build();

            // 保存到数据库
            boolean success = save(operationLog);

            if (success) {
                log.info("操作日志记录成功 - 操作人: {}, 操作: {}, 状态: {}, IP: {}, 记录ID: {}",
                    operator, action, status, ip, operationLog.getId());
            } else {
                log.error("操作日志记录保存失败 - 操作人: {}, 操作: {}", operator, action);
            }
        } catch (Exception e) {
            log.error("记录操作日志时发生异常 - 操作人: {}, 操作: {}, 异常信息: {}",
                operator, action, e.getMessage(), e);
        }
    }

    /**
     * 分页获取所有操作日志记录
     * 支持按操作类型、操作人、状态筛选
     *
     * @param pageNum 页码，从1开始
     * @param pageSize 每页记录数
     * @param action 操作类型筛选（可选）
     * @param operator 操作人筛选（可选）
     * @param status 状态筛选（可选）
     * @return 包含操作日志分页结果的对象
     */
    @Override
    public Result<IPage<OperationLogVO>> getAllLogs(Integer pageNum, Integer pageSize, String action, String operator, Integer status) {
        log.info("开始获取所有操作日志，页码: {}, 每页大小: {}, 操作类型: {}, 操作人: {}, 状态: {}",
            pageNum, pageSize, action, operator, status);

        try {
            // 参数校验和默认值设置
            if (pageNum == null || pageNum < 1) {
                pageNum = 1;
            }
            if (pageSize == null || pageSize < 1) {
                pageSize = 20;
            }
            if (pageSize > 100) {
                pageSize = 100;
            }

            // 创建分页查询对象
            Page<OperationLog> page = new Page<>(pageNum, pageSize);

            // 执行查询，按创建时间降序排列
            IPage<OperationLog> result = lambdaQuery()
                    .like(action != null && !action.isEmpty(), OperationLog::getAction, action)
                    .like(operator != null && !operator.isEmpty(), OperationLog::getOperator, operator)
                    .eq(status != null, OperationLog::getStatus, status)
                    .orderByDesc(OperationLog::getCreateTime)
                    .page(page);

            // 转换为VO对象
            List<OperationLogVO> voList = result.getRecords().stream()
                    .map(this::convertToVO)
                    .collect(Collectors.toList());

            // 构建返回结果
            Page<OperationLogVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
            voPage.setRecords(voList);

            log.info("成功获取操作日志，当前页 {} 条记录，总记录数: {}", voList.size(), result.getTotal());
            return Result.success(voPage);
        } catch (Exception e) {
            log.error("获取操作日志时发生异常: {}", e.getMessage(), e);
            return Result.error("获取操作日志失败：" + e.getMessage());
        }
    }

    /**
     * 获取最近的操作日志记录
     * 按时间降序返回最新的操作日志，用于展示最近系统活动
     *
     * @param limit 返回记录数限制，默认10条，最大100条
     * @return 包含最近操作日志列表的结果对象
     */
    @Override
    public Result<List<OperationLogVO>> getRecentLogs(Integer limit) {
        log.info("开始获取最近的操作日志，限制条数: {}", limit);

        try {
            // 参数校验和默认值设置
            if (limit == null || limit < 1) {
                log.debug("限制条数参数无效，使用默认值10");
                limit = 10;
            }
            if (limit > 100) {
                log.debug("限制条数超过最大值，设置为100");
                limit = 100;
            }

            // 创建分页查询对象
            log.debug("创建分页查询，页码: 1, 每页大小: {}", limit);
            Page<OperationLog> page = new Page<>(1, limit);

            // 执行查询，按创建时间降序排列
            log.debug("执行最近操作日志查询");
            IPage<OperationLog> result = lambdaQuery()
                    .orderByDesc(OperationLog::getCreateTime)
                    .page(page);

            // 转换为VO对象
            log.debug("开始转换操作日志记录为VO对象");
            List<OperationLogVO> voList = result.getRecords().stream()
                    .map(this::convertToVO)
                    .collect(Collectors.toList());

            log.info("成功获取最近操作日志，共 {} 条记录", voList.size());
            return Result.success(voList);
        } catch (Exception e) {
            log.error("获取最近操作日志时发生异常，限制条数: {}, 异常信息: {}", limit, e.getMessage(), e);
            return Result.error("获取操作日志失败：" + e.getMessage());
        }
    }

    /**
     * 获取指定用户的操作日志记录
     * 分页查询指定操作人的操作历史，按时间降序排列
     *
     * @param operator 操作人标识（用户名或用户ID）
     * @param pageNum 页码，从1开始
     * @param pageSize 每页记录数，默认10条，最大100条
     * @return 包含指定用户操作日志列表的结果对象
     */
    @Override
    public Result<List<OperationLogVO>> getUserLogs(String operator, Integer pageNum, Integer pageSize) {
        log.info("开始获取用户操作日志，操作人: {}, 页码: {}, 每页大小: {}", operator, pageNum, pageSize);

        try {
            // 参数校验和默认值设置
            if (pageNum == null || pageNum < 1) {
                log.debug("页码参数无效，使用默认值1");
                pageNum = 1;
            }
            if (pageSize == null || pageSize < 1) {
                log.debug("每页大小参数无效，使用默认值10");
                pageSize = 10;
            }
            if (pageSize > 100) {
                log.debug("每页大小超过最大值，设置为100");
                pageSize = 100;
            }

            // 创建分页查询对象
            log.debug("创建分页查询，页码: {}, 每页大小: {}", pageNum, pageSize);
            Page<OperationLog> page = new Page<>(pageNum, pageSize);

            // 执行查询，按创建时间降序排列
            log.debug("执行用户 {} 的操作日志分页查询", operator);
            IPage<OperationLog> result = lambdaQuery()
                    .eq(OperationLog::getOperator, operator)
                    .orderByDesc(OperationLog::getCreateTime)
                    .page(page);

            // 转换为VO对象
            log.debug("开始转换用户操作日志记录为VO对象");
            List<OperationLogVO> voList = result.getRecords().stream()
                    .map(this::convertToVO)
                    .collect(Collectors.toList());

            log.info("成功获取用户 {} 的操作日志，共 {} 条记录，当前页 {} 条记录，总记录数: {}",
                operator, voList.size(), result.getTotal(), result.getTotal());
            return Result.success(voList);
        } catch (Exception e) {
            log.error("获取用户操作日志时发生异常，操作人: {}, 页码: {}, 每页大小: {}, 异常信息: {}",
                operator, pageNum, pageSize, e.getMessage(), e);
            return Result.error("获取操作日志失败：" + e.getMessage());
        }
    }

    /**
     * 清空所有操作日志记录
     * 危险操作：删除数据库中的所有操作日志记录，请谨慎使用
     *
     * @return 清空操作的结果对象
     */
    @Override
    public Result<String> clearLogs() {
        log.warn("开始执行清空操作日志操作（危险操作）");

        try {
            log.debug("开始删除所有操作日志记录");
            remove(null);

            log.warn("操作日志已全部清空，请注意此操作不可恢复");
            return Result.success("日志已清空");
        } catch (Exception e) {
            log.error("清空操作日志时发生异常: {}", e.getMessage(), e);
            return Result.error("清空日志失败：" + e.getMessage());
        }
    }

    /**
     * 将操作日志实体转换为视图对象
     * 格式化输出结果，去除敏感信息或长字符串截断
     *
     * @param operationLog 操作日志实体对象
     * @return 转换后的操作日志视图对象
     */
    private OperationLogVO convertToVO(OperationLog operationLog) {
        log.debug("开始转换操作日志记录为VO对象，记录ID: {}", operationLog.getId());

        try {
            // 构建VO对象
            OperationLogVO vo = OperationLogVO.builder()
                    .id(operationLog.getId())
                    .operator(operationLog.getOperator())
                    .action(operationLog.getAction())
                    .detail(operationLog.getDetail())
                    .status(operationLog.getStatus())
                    .ipAddress(operationLog.getIpAddress())
                    .userAgent(operationLog.getUserAgent())
                    .timestamp(operationLog.getCreateTime())
                    .build();

            log.debug("操作日志记录转换完成，记录ID: {}, 操作人: {}, 操作: {}, 状态: {}",
                operationLog.getId(), operationLog.getOperator(), operationLog.getAction(), operationLog.getStatus());

            return vo;
        } catch (Exception e) {
            log.error("转换操作日志记录为VO对象时发生异常，记录ID: {}, 异常信息: {}",
                operationLog.getId(), e.getMessage(), e);
            // 返回一个基本的VO对象，避免因转换失败导致整个查询失败
            return OperationLogVO.builder()
                    .id(operationLog.getId())
                    .operator("转换失败")
                    .action("未知")
                    .detail("转换失败: " + e.getMessage())
                    .status(0)
                    .ipAddress("未知")
                    .userAgent(null)
                    .timestamp(operationLog.getCreateTime())
                    .build();
        }
    }
}
