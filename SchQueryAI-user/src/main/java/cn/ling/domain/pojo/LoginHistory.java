package cn.ling.domain.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 用户登录历史记录表
 * @TableName login_history
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "login_history")
public class LoginHistory {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
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
     * 登录IP地址
     */
    private String loginIp;

    /**
     * 登录国家
     */
    private String country;

    /**
     * 登录省份/州
     */
    private String province;

    /**
     * 登录城市
     */
    private String city;

    /**
     * 运营商
     */
    private String isp;

    /**
     * 登录时间
     */
    private Date loginTime;

    /**
     * 登录状态 (1-成功, 0-失败)
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

    /**
     * 创建时间
     */
    private Date createTime;
}
