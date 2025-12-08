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
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/**
 * 登录历史服务实现类
 * 管理用户登录历史记录，包括登录成功/失败记录、异常登录检测等功能
 * 提供登录历史的查询、分页展示和异常行为监控
 */
@Slf4j
@Service
public class LoginHistoryServiceImpl extends ServiceImpl<LoginHistoryMapper, LoginHistory>
        implements LoginHistoryService {

    /**
     * 记录用户登录历史
     * 保存用户的登录成功或失败记录，包括IP地址、设备信息、登录时间等
     *
     * @param loginHistory 登录历史记录对象，包含用户ID、IP、状态等信息
     */
    @Override
    public void recordLoginHistory(LoginHistory loginHistory) {
        log.info("开始记录用户登录历史，用户ID: {}, 登录IP: {}, 登录状态: {}",
            loginHistory.getUserId(), loginHistory.getLoginIp(),
            loginHistory.getStatus() == 1 ? "成功" : "失败");

        try {
            log.debug("准备保存登录历史记录到数据库");
            boolean success = save(loginHistory);

            if (success) {
                log.info("登录历史记录保存成功，记录ID: {}, 用户ID: {}",
                    loginHistory.getId(), loginHistory.getUserId());
            } else {
                log.error("登录历史记录保存失败，用户ID: {}, 登录IP: {}",
                    loginHistory.getUserId(), loginHistory.getLoginIp());
            }
        } catch (Exception e) {
            log.error("记录登录历史时发生异常，用户ID: {}, 登录IP: {}, 异常信息: {}",
                loginHistory.getUserId(), loginHistory.getLoginIp(), e.getMessage(), e);
        }
    }

    /**
     * 获取指定用户的登录历史记录
     * 分页查询用户的登录历史，按登录时间降序排列
     *
     * @param userId 用户ID
     * @param pageNum 页码，从1开始
     * @param pageSize 每页记录数
     * @return 包含登录历史列表的结果对象
     */
    @Override
    public Result<List<LoginHistoryVO>> getUserLoginHistory(Long userId, Integer pageNum, Integer pageSize) {
        log.info("开始查询用户登录历史，用户ID: {}, 页码: {}, 每页大小: {}", userId, pageNum, pageSize);

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

            // 创建分页对象
            log.debug("创建分页查询对象，页码: {}, 每页大小: {}", pageNum, pageSize);
            Page<LoginHistory> page = new Page<>(pageNum, pageSize);

            // 执行分页查询
            log.debug("执行用户 {} 的登录历史分页查询", userId);
            Page<LoginHistory> result = lambdaQuery()
                    .eq(LoginHistory::getUserId, userId)
                    .orderByDesc(LoginHistory::getLoginTime)
                    .page(page);

            // 转换为VO对象
            log.debug("开始转换登录历史记录为VO对象");
            List<LoginHistoryVO> voList = result.getRecords().stream()
                    .map(this::convertToVO)
                    .collect(Collectors.toList());

            log.info("成功查询用户 {} 的登录历史，共 {} 条记录，当前页 {} 条记录",
                userId, result.getTotal(), voList.size());
            return Result.success(voList, "查询成功");
        } catch (Exception e) {
            log.error("查询用户登录历史时发生异常，用户ID: {}, 页码: {}, 每页大小: {}, 异常信息: {}",
                userId, pageNum, pageSize, e.getMessage(), e);
            return Result.error("查询登录历史失败：" + e.getMessage());
        }
    }

    /**
     * 获取当前登录用户的登录历史记录
     * 从上下文中获取当前用户ID，然后查询该用户的登录历史
     *
     * @param pageNum 页码，从1开始
     * @param pageSize 每页记录数
     * @return 包含当前用户登录历史列表的结果对象
     */
    @Override
    public Result<List<LoginHistoryVO>> getMyLoginHistory(Integer pageNum, Integer pageSize) {
        log.info("开始获取当前用户的登录历史记录");

        try {
            // 从上下文中获取当前用户ID
            Long userId = ContextUtils.getUserId();
            log.info("获取到当前用户ID: {}, 开始查询登录历史", userId);

            return getUserLoginHistory(userId, pageNum, pageSize);
        } catch (Exception e) {
            log.error("获取当前用户登录历史时发生异常，页码: {}, 每页大小: {}, 异常信息: {}",
                pageNum, pageSize, e.getMessage(), e);
            return Result.error("获取登录历史失败：" + e.getMessage());
        }
    }

    /**
     * 检测异常登录行为
     * 通过比较最近3次成功登录的城市与当前登录城市，判断是否存在异地登录风险
     *
     * @param userId 用户ID
     * @param currentIp 当前登录IP地址
     * @param currentCity 当前登录城市
     * @return 如果检测到异常登录返回true，否则返回false
     */
    @Override
    public boolean detectAbnormalLogin(Long userId, String currentIp, String currentCity) {
        log.debug("开始检测用户 {} 的异常登录行为，当前IP: {}, 当前城市: {}", userId, currentIp, currentCity);

        try {
            // 获取最近的登录记录（最近3次成功登录）
            log.debug("查询用户 {} 最近3次成功登录记录", userId);
            List<LoginHistory> recentLogins = lambdaQuery()
                    .eq(LoginHistory::getUserId, userId)
                    .eq(LoginHistory::getStatus, 1) // 只查询成功登录
                    .orderByDesc(LoginHistory::getLoginTime)
                    .last("LIMIT 3")
                    .list();

            // 如果没有历史记录，不算异常
            if (recentLogins.isEmpty()) {
                log.info("用户 {} 没有历史登录记录，本次登录不视为异常", userId);
                return false;
            }

            log.info("查询到用户 {} 的 {} 条历史登录记录", userId, recentLogins.size());

            // 检查最近的登录城市是否与当前城市不同
            for (LoginHistory history : recentLogins) {
                log.debug("检查历史登录记录 - 时间: {}, 城市: {}, 当前城市: {}",
                    history.getLoginTime(), history.getCity(), currentCity);

                if (history.getCity() != null && !history.getCity().equals(currentCity)) {
                    log.warn("检测到异地登录风险 - 用户ID: {}, 历史城市: {}, 当前城市: {}, IP: {}",
                        userId, history.getCity(), currentCity, currentIp);
                    return true;
                }
            }

            log.info("用户 {} 登录行为正常，未检测到异常", userId);
            return false;
        } catch (Exception e) {
            log.error("检测异常登录行为时发生异常，用户ID: {}, 当前IP: {}, 当前城市: {}, 异常信息: {}",
                userId, currentIp, currentCity, e.getMessage(), e);
            // 异常情况下为了安全起见，返回true（视为异常）
            return true;
        }
    }

    /**
     * 将登录历史实体转换为视图对象
     * 整合地理位置信息，格式化输出结果
     *
     * @param history 登录历史实体对象
     * @return 转换后的登录历史视图对象
     */
    private LoginHistoryVO convertToVO(LoginHistory history) {
        log.debug("开始转换登录历史记录为VO对象，记录ID: {}", history.getId());

        try {
            // 格式化地理位置信息
            String location = IpLocationUtils.formatLocation(
                    history.getCountry(),
                    history.getProvince(),
                    history.getCity()
            );

            // 构建VO对象
            LoginHistoryVO vo = LoginHistoryVO.builder()
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

            log.debug("登录历史记录转换完成，记录ID: {}, 用户ID: {}, 登录IP: {}, 位置: {}",
                history.getId(), history.getUserId(), history.getLoginIp(), location);

            return vo;
        } catch (Exception e) {
            log.error("转换登录历史记录为VO对象时发生异常，记录ID: {}, 异常信息: {}",
                history.getId(), e.getMessage(), e);
            // 返回一个基本的VO对象，避免因转换失败导致整个查询失败
            return LoginHistoryVO.builder()
                    .id(history.getId())
                    .userId(history.getUserId())
                    .userName(history.getUserName())
                    .loginIp(history.getLoginIp())
                    .location("未知")
                    .loginTime(history.getLoginTime())
                    .status(history.getStatus())
                    .failReason(history.getFailReason())
                    .userAgent(history.getUserAgent())
                    .build();
        }
    }

    /**
     * 获取每日访问量统计（最近N天）
     * 统计每天的登录次数，用于生成访问量趋势图
     *
     * @param days 天数
     * @return 每日访问量列表
     */
    @Override
    public Result<List<Map<String, Object>>> getDailyVisitStats(Integer days) {
        log.info("开始获取每日访问量统计，天数: {}", days);

        try {
            if (days == null || days < 1) {
                days = 7;
            }
            if (days > 30) {
                days = 30;
            }

            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(days - 1);
            LocalDateTime startDateTime = startDate.atStartOfDay();

            // 查询最近N天的所有登录记录
            List<LoginHistory> loginList = lambdaQuery()
                    .ge(LoginHistory::getLoginTime, startDateTime)
                    .eq(LoginHistory::getStatus, 1) // 只统计成功登录
                    .list();

            // 按日期分组统计
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
            Map<String, Long> dailyCount = new HashMap<>();

            // 初始化所有日期为0
            for (int i = 0; i < days; i++) {
                LocalDate date = startDate.plusDays(i);
                dailyCount.put(date.format(formatter), 0L);
            }

            // 统计每天的登录数
            for (LoginHistory login : loginList) {
                String dateKey = login.getLoginTime().toLocalDate().format(formatter);
                dailyCount.merge(dateKey, 1L, Long::sum);
            }

            // 转换为结果列表
            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = 0; i < days; i++) {
                LocalDate date = startDate.plusDays(i);
                String dateKey = date.format(formatter);
                Map<String, Object> item = new HashMap<>();
                item.put("date", dateKey);
                item.put("count", dailyCount.get(dateKey));
                result.add(item);
            }

            log.info("成功获取每日访问量统计，共 {} 天数据", result.size());
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取每日访问量统计失败: {}", e.getMessage(), e);
            return Result.error("获取访问量统计失败：" + e.getMessage());
        }
    }

    /**
     * 获取每小时访问量统计（最近N小时）
     * 统计每小时的登录次数，用于生成访问量趋势图
     *
     * @param hours 小时数
     * @return 每小时访问量列表
     */
    @Override
    public Result<List<Map<String, Object>>> getHourlyVisitStats(Integer hours) {
        log.info("开始获取每小时访问量统计，小时数: {}", hours);

        try {
            if (hours == null || hours < 1) {
                hours = 12;
            }
            if (hours > 24) {
                hours = 24;
            }

            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusHours(hours - 1).withMinute(0).withSecond(0).withNano(0);

            // 查询最近N小时的所有登录记录
            List<LoginHistory> loginList = lambdaQuery()
                    .ge(LoginHistory::getLoginTime, startTime)
                    .eq(LoginHistory::getStatus, 1) // 只统计成功登录
                    .list();

            // 按小时分组统计
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:00");
            Map<String, Long> hourlyCount = new HashMap<>();

            // 初始化所有小时为0
            for (int i = 0; i < hours; i++) {
                LocalDateTime time = startTime.plusHours(i);
                hourlyCount.put(time.format(formatter), 0L);
            }

            // 统计每小时的登录数
            for (LoginHistory login : loginList) {
                String hourKey = login.getLoginTime().withMinute(0).withSecond(0).withNano(0).format(formatter);
                hourlyCount.merge(hourKey, 1L, Long::sum);
            }

            // 转换为结果列表
            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = 0; i < hours; i++) {
                LocalDateTime time = startTime.plusHours(i);
                String hourKey = time.format(formatter);
                Map<String, Object> item = new HashMap<>();
                item.put("date", hourKey);
                item.put("count", hourlyCount.get(hourKey));
                result.add(item);
            }

            log.info("成功获取每小时访问量统计，共 {} 小时数据", result.size());
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取每小时访问量统计失败: {}", e.getMessage(), e);
            return Result.error("获取访问量统计失败：" + e.getMessage());
        }
    }

    /**
     * 获取最近的登录记录
     * 用于展示最近访问的IP列表
     *
     * @param limit 数量限制
     * @return 最近登录记录列表
     */
    @Override
    public Result<List<LoginHistoryVO>> getRecentLogins(Integer limit) {
        log.info("开始获取最近登录记录，限制: {}", limit);

        try {
            if (limit == null || limit < 1) {
                limit = 10;
            }
            if (limit > 50) {
                limit = 50;
            }

            // 查询最近的登录记录
            Page<LoginHistory> page = new Page<>(1, limit);
            Page<LoginHistory> result = lambdaQuery()
                    .eq(LoginHistory::getStatus, 1) // 只查询成功登录
                    .orderByDesc(LoginHistory::getLoginTime)
                    .page(page);

            // 转换为VO对象
            List<LoginHistoryVO> voList = result.getRecords().stream()
                    .map(this::convertToVO)
                    .collect(Collectors.toList());

            log.info("成功获取最近登录记录，共 {} 条", voList.size());
            return Result.success(voList);
        } catch (Exception e) {
            log.error("获取最近登录记录失败: {}", e.getMessage(), e);
            return Result.error("获取最近登录记录失败：" + e.getMessage());
        }
    }
}
