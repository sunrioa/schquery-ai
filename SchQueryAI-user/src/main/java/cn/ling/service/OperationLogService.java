package cn.ling.service;

import cn.ling.Result;
import cn.ling.domain.vo.OperationLogVO;
import com.baomidou.mybatisplus.extension.service.IService;
import cn.ling.domain.pojo.OperationLog;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 操作日志服务接口
 */
public interface OperationLogService extends IService<OperationLog> {

    /**
     * 记录操作日志
     *
     * @param operator 操作人
     * @param action 操作类型
     * @param detail 操作详情
     * @param status 操作状态
     * @param request HTTP请求
     */
    void recordLog(String operator, String action, String detail, Integer status, HttpServletRequest request);

    /**
     * 获取最近的操作日志
     *
     * @param limit 数量限制
     * @return 操作日志列表
     */
    Result<List<OperationLogVO>> getRecentLogs(Integer limit);

    /**
     * 获取用户的操作日志
     *
     * @param operator 操作人
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 操作日志列表
     */
    Result<List<OperationLogVO>> getUserLogs(String operator, Integer pageNum, Integer pageSize);

    /**
     * 清空操作日志
     *
     * @return 结果
     */
    Result<String> clearLogs();
}
