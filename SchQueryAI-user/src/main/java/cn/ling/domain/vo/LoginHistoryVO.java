package cn.ling.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 登录历史记录VO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoginHistoryVO {
    /**
     * 记录ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 登录IP
     */
    private String loginIp;

    /**
     * 登录地点
     */
    private String location;

    /**
     * 登录时间
     */
    private LocalDateTime loginTime;

    /**
     * 登录状态
     */
    private Integer status;

    /**
     * 失败原因
     */
    private String failReason;

    /**
     * 浏览器信息
     */
    private String userAgent;
}
