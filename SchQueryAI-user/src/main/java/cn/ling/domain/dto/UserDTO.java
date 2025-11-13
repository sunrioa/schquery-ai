package cn.ling.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户数据传输对象
 * 用于接收前端传递的用户相关数据，包括注册、登录等场景
 * 包含用户基本信息、密码验证、邮箱验证等字段
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {

    /**
     * 用户名
     * 用于用户登录和身份标识的唯一名称
     */
    private String userName;

    /**
     * 用户密码
     * 用户登录密码，应该在前端进行加密传输
     */
    private String password;

    /**
     * 确认密码
     * 用于注册时确认用户输入的密码是否正确
     */
    private String rePassword;

    /**
     * 电子邮箱
     * 用户邮箱地址，用于接收验证码和系统通知
     */
    private String email;

    /**
     * 验证码
     * 邮箱验证码，用于验证用户邮箱的有效性
     */
    private String code;

    /**
     * 用户角色
     * 用户的权限角色，如：user（普通用户）、worker（工作人员）、admin（管理员）
     */
    private String role;

}
