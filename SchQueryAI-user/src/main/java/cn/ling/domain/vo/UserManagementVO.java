package cn.ling.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 用户管理VO - 管理员查看用户列表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserManagementVO {

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 角色
     */
    private String role;

    /**
     * 头像ID
     */
    private Long avatar;

    /**
     * 最后登录IP
     */
    private String lastLoginIp;

    /**
     * 最后登录时间
     */
    private Date lastLoginTime;

    /**
     * 最后登录国家
     */
    private String lastLoginCountry;

    /**
     * 最后登录省份
     */
    private String lastLoginProvince;

    /**
     * 最后登录城市
     */
    private String lastLoginCity;

    /**
     * 最后登录地点（格式化后）
     */
    private String lastLoginLocation;

    /**
     * 注册时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 账号状态 1-正常 0-弃用
     */
    private Integer status;
}
