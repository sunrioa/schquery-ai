package cn.ling.service.impl;

import cn.ling.Result;
import cn.ling.domain.pojo.LoginHistory;
import cn.ling.domain.vo.LoginHistoryVO;
import cn.ling.mapper.LoginHistoryMapper;
import cn.ling.service.LoginHistoryService;
import cn.ling.utils.ContextUtils;
import cn.ling.utils.IpLocationUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @description 针对表【login_history(用户登录历史记录表)】的数据库操作Service实现
 */
@Slf4j
@Service
public class LoginHistoryServiceImpl extends ServiceImpl<LoginHistoryMapper, LoginHistory>
        implements LoginHistoryService {

    @Override
    public void recordLoginHistory(LoginHistory loginHistory) {
        try {
            save(loginHistory);
        } catch (Exception e) {
            log.error("记录登录历史失败", e);
        }
    }

    @Override
    public Result<List<LoginHistoryVO>> getUserLoginHistory(Long userId, Integer pageNum, Integer pageSize) {
        if (pageNum == null || pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = 10;
        }

        Page<LoginHistory> page = new Page<>(pageNum, pageSize);
        Page<LoginHistory> result = lambdaQuery()
                .eq(LoginHistory::getUserId, userId)
                .orderByDesc(LoginHistory::getLoginTime)
                .page(page);

        List<LoginHistoryVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return Result.success(voList, "查询成功");
    }

    @Override
    public Result<List<LoginHistoryVO>> getMyLoginHistory(Integer pageNum, Integer pageSize) {
        Long userId = ContextUtils.getUserId();
        return getUserLoginHistory(userId, pageNum, pageSize);
    }

    @Override
    public boolean detectAbnormalLogin(Long userId, String currentIp, String currentCity) {
        // 获取最近的登录记录（最近3次）
        List<LoginHistory> recentLogins = lambdaQuery()
                .eq(LoginHistory::getUserId, userId)
                .eq(LoginHistory::getStatus, 1)
                .orderByDesc(LoginHistory::getLoginTime)
                .last("LIMIT 3")
                .list();

        // 如果没有历史记录，不算异常
        if (recentLogins.isEmpty()) {
            return false;
        }

        // 检查最近的登录城市是否与当前城市不同
        for (LoginHistory history : recentLogins) {
            if (history.getCity() != null && !history.getCity().equals(currentCity)) {
                log.warn("检测到异地登录 - 用户ID: {}, 历史城市: {}, 当前城市: {}", 
                        userId, history.getCity(), currentCity);
                return true;
            }
        }

        return false;
    }

    /**
     * 转换为VO对象
     */
    private LoginHistoryVO convertToVO(LoginHistory history) {
        String location = IpLocationUtils.formatLocation(
                history.getCountry(),
                history.getProvince(),
                history.getCity()
        );

        return LoginHistoryVO.builder()
                .id(history.getId())
                .userId(history.getUserId())
                .userName(history.getUserName())
                .loginIp(history.getLoginIp())
                .location(location)
                .loginTime(history.getLoginTime())
                .status(history.getStatus())
                .failReason(history.getFailReason())
                .userAgent(history.getUserAgent())
                .build();
    }
}
