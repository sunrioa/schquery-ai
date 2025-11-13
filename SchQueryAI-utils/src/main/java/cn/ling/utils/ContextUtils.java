package cn.ling.utils;

import cn.ling.role.UserInfo;
import lombok.extern.slf4j.Slf4j;

/**
 * 线程上下文工具类
 * 使用ThreadLocal在当前线程中存储用户上下文信息，实现跨方法调用的用户身份传递
 * 提供用户信息的设置、获取、清除等操作，确保请求处理过程中的用户上下文一致性
 * 注意：必须在请求处理完成后调用clear()方法，避免内存泄漏
 */
@Slf4j
public class ContextUtils {
    /**
     * 线程本地存储，用于在当前线程中存储用户上下文信息
     * 确保在并发环境下每个线程都有独立的用户信息副本
     */
    private static final ThreadLocal<UserInfo> USER_CONTEXT = new ThreadLocal<>();

    /**
     * 设置当前线程的用户信息
     * 通常在用户认证成功后调用，将用户信息存储到线程上下文中
     * 以便在后续的业务处理中获取当前用户身份
     *
     * @param userInfo 用户信息对象，包含用户ID、用户名等信息
     * @throws IllegalArgumentException 当userInfo为null时抛出异常
     */
    public static void setUserInfo(UserInfo userInfo) {
        log.debug("开始设置当前线程的用户信息");

        if (userInfo == null) {
            log.error("设置用户信息失败：用户信息不能为null");
            throw new IllegalArgumentException("用户信息不能为null");
        }

        log.info("设置用户信息到线程上下文 - 用户ID: {}, 用户名: {}",
            userInfo.getUserId(), userInfo.getUsername());

        USER_CONTEXT.set(userInfo);
        log.debug("用户信息设置完成，线程ID: {}", Thread.currentThread().getId());
    }

    /**
     * 获取当前线程的用户信息
     * 从线程本地存储中获取当前用户的信息，用于身份认证和授权
     *
     * @return 用户信息对象，如果未设置则返回null
     */
    public static UserInfo getUserInfo() {
        log.debug("获取当前线程的用户信息，线程ID: {}", Thread.currentThread().getId());

        UserInfo userInfo = USER_CONTEXT.get();

        if (userInfo != null) {
            log.debug("成功获取用户信息 - 用户ID: {}, 用户名: {}",
                userInfo.getUserId(), userInfo.getUsername());
        } else {
            log.warn("当前线程未设置用户信息，线程ID: {}", Thread.currentThread().getId());
        }

        return userInfo;
    }

    /**
     * 清除当前线程的用户信息（必须调用，避免内存泄漏）
     * 在请求处理完成后必须调用此方法，防止ThreadLocal导致的内存泄漏
     * 建议在拦截器的afterCompletion或filter的finally块中调用
     */
    public static void clear() {
        log.debug("开始清除当前线程的用户信息，线程ID: {}", Thread.currentThread().getId());

        UserInfo userInfo = USER_CONTEXT.get();
        if (userInfo != null) {
            log.info("清除用户信息 - 用户ID: {}, 用户名: {}, 线程ID: {}",
                userInfo.getUserId(), userInfo.getUsername(), Thread.currentThread().getId());
        }

        USER_CONTEXT.remove();
        log.debug("用户信息清除完成，线程ID: {}", Thread.currentThread().getId());
    }

    /**
     * 便捷方法：直接获取当前用户的ID
     * 提供快速访问用户ID的方法，无需手动获取UserInfo对象
     *
     * @return 当前用户的ID，如果未设置用户信息则返回null
     * @throws IllegalStateException 如果用户信息未设置则抛出异常
     */
    public static Long getUserId() {
        log.debug("通过便捷方法获取用户ID，线程ID: {}", Thread.currentThread().getId());

        UserInfo userInfo = getUserInfo();

        if (userInfo == null) {
            log.error("无法获取用户ID：当前线程未设置用户信息，线程ID: {}", Thread.currentThread().getId());
            throw new IllegalStateException("当前线程未设置用户信息");
        }

        Long userId = userInfo.getUserId();
        log.debug("成功获取用户ID: {}", userId);
        return userId;
    }

    /**
     * 便捷方法：直接获取当前用户的用户名
     * 提供快速访问用户名的方法，无需手动获取UserInfo对象
     *
     * @return 当前用户的用户名，如果未设置用户信息则返回null
     * @throws IllegalStateException 如果用户信息未设置则抛出异常
     */
    public static String getUsername() {
        log.debug("通过便捷方法获取用户名，线程ID: {}", Thread.currentThread().getId());

        UserInfo userInfo = getUserInfo();

        if (userInfo == null) {
            log.error("无法获取用户名：当前线程未设置用户信息，线程ID: {}", Thread.currentThread().getId());
            throw new IllegalStateException("当前线程未设置用户信息");
        }

        String username = userInfo.getUsername();
        log.debug("成功获取用户名: {}", username);
        return username;
    }
}
