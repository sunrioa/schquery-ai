package cn.ling.service;

import cn.ling.Result;
import cn.ling.domain.pojo.LoginHistory;
import cn.ling.domain.vo.LoginHistoryVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

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
}
