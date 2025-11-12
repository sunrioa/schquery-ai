package cn.ling.domain.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 系统用户表
 * @TableName user
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(value ="user")
public class User {
    /**
     * 用户主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 密码（建议加密存储）
     */
    private String passWord;

    /**
     * 电子邮箱
     */
    private String email;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 角色
     */
    private String role;

    /**
     * 头像
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
     * 用户状态 1-正常 0-弃用
     */
    private Integer status = 1;

}