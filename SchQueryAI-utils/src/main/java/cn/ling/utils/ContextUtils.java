package cn.ling.utils;

import cn.ling.role.UserInfo;

public class ContextUtils {
    // 定义ThreadLocal，泛型为UserInfo（存储当前线程的用户信息）
    private static final ThreadLocal<UserInfo> USER_CONTEXT = new ThreadLocal<>();

    /**
     * 设置当前线程的用户信息
     */
    public static void setUserInfo(UserInfo userInfo) {
        USER_CONTEXT.set(userInfo);
    }

    /**
     * 获取当前线程的用户信息
     * @return 用户信息（如果未设置，返回null）
     */
    public static UserInfo getUserInfo() {
        return USER_CONTEXT.get();
    }

    /**
     * 清除当前线程的用户信息（必须调用，避免内存泄漏）
     */
    public static void clear() {
        USER_CONTEXT.remove();
    }

    // 便捷方法：直接获取用户ID
    public static Long getUserId() {
        return getUserInfo().getUserId() ;
    }

    // 便捷方法：直接获取用户名
    public static String getUsername() {
        return getUserInfo().getUsername() ;
    }
}
