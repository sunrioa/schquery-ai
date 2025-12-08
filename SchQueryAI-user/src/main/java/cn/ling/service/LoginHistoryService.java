package cn.ling.service;

import cn.ling.Result;
import cn.ling.domain.pojo.LoginHistory;
import cn.ling.domain.vo.LoginHistoryVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * @description 针对表【login_history(用户登录历史记录表)】的数据库操作Service
 */
public interface LoginHistoryService extends IService<LoginHistory> {

    /**
     * 记录登录历史
     */
    void recordLoginHistory(LoginHistory loginHistory);

    /**
     * 获取用户登录历史
     */
    Result<List<LoginHistoryVO>> getUserLoginHistory(Long userId, Integer pageNum, Integer pageSize);

    /**
     * 获取当前用户登录历史
     */
    Result<List<LoginHistoryVO>> getMyLoginHistory(Integer pageNum, Integer pageSize);

    /**
     * 检测异地登录
     */
    boolean detectAbnormalLogin(Long userId, String currentIp, String currentCity);

    /**
     * 获取每日访问量统计（最近N天）
     * @param days 天数
     * @return 每日访问量列表
     */
    Result<List<Map<String, Object>>> getDailyVisitStats(Integer days);

    /**
     * 获取每小时访问量统计（最近N小时）
     * @param hours 小时数
     * @return 每小时访问量列表
     */
    Result<List<Map<String, Object>>> getHourlyVisitStats(Integer hours);

    /**
     * 获取最近的登录记录
     * @param limit 数量限制
     * @return 最近登录记录列表
     */
    Result<List<LoginHistoryVO>> getRecentLogins(Integer limit);
}
