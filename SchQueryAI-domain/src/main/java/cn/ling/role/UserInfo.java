package cn.ling.role;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息封装类
 * 用于存储和传递当前登录用户的基本信息
 * 通常与ThreadLocal配合使用，在请求处理过程中保存用户上下文
 * 支持用户认证、权限控制和业务操作中的身份识别
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {

    /**
     * 用户ID
     * 系统中用户的唯一标识符，通常对应数据库中的主键
     * 用于用户相关的数据库操作和业务逻辑处理
     */
    private Long userId;

    /**
     * 用户名
     * 用户的登录名称或显示名称，用于界面展示和日志记录
     * 可能是邮箱、手机号或自定义的用户名
     */
    private String username;

    /**
     * 用户角色
     * 用户的权限角色，用于访问控制和权限判断
     * 常见角色：user（普通用户）、admin（管理员）、worker（客服）等
     */
    private String role;

}
