package cn.ling.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 仪表板统计信息VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsVO {

    /**
     * 总用户数
     */
    private Long totalUsers;

    /**
     * 今日新增用户数
     */
    private Long todayNewUsers;

    /**
     * 活跃用户数（24小时内登录）
     */
    private Long activeUsers;

    /**
     * 管理员数
     */
    private Long adminCount;
}
