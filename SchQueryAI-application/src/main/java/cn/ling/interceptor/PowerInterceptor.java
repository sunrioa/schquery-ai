package cn.ling.interceptor;

import cn.ling.utils.ContextUtils;
import cn.ling.exception.CustomException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 权限校验拦截器
 * 负责根据用户角色和请求路径进行权限验证
 * 支持三级权限体系：user（普通用户）、worker（工作人员）、admin（管理员）
 */
@Slf4j
public class PowerInterceptor implements HandlerInterceptor {
    /**
     * 请求处理前执行（权限校验）
     * 根据请求路径和用户角色进行权限验证
     *
     * @param request HTTP请求对象
     * @param response HTTP响应对象
     * @param handler 处理器对象
     * @return true：权限验证通过；false：权限验证失败
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestURI = request.getRequestURI();
        String method = request.getMethod();
        log.debug("开始权限校验，请求路径: {} {}", method, requestURI);

        // 跳过OPTIONS请求（CORS预检请求）
        if ("OPTIONS".equals(method)) {
            log.debug("跳过OPTIONS请求: {}", requestURI);
            return true;
        }

        // 解析请求路径，提取第一级路径段
        String[] pathSegments = requestURI.split("/");
        log.debug("请求路径分段: {}", (Object) pathSegments);

        // 获取当前用户角色
        String role = ContextUtils.getUserInfo().getRole();
        log.debug("当前用户角色: {}, 请求路径第一段: {}",
                role, pathSegments.length > 1 ? pathSegments[1] : "空");

        // 1. /user/** 路径：所有用户都可访问
        if (pathSegments.length > 1 && "user".equals(pathSegments[1])) {
            log.debug("访问用户级别路径，放行: {}", requestURI);
            return true;
        }

        // 2. /worker/** 路径：需要 worker 或 admin 角色
        if (pathSegments.length > 1 && "worker".equals(pathSegments[1])) {
            if ("user".equals(role)) {
                log.warn("普通用户尝试访问工作人员路径，拒绝访问: {}，用户角色: {}", requestURI, role);
                throw CustomException.error("权限不足");
            }
            log.debug("工作人员级别路径访问通过: {}，用户角色: {}", requestURI, role);
        }

        // 3. /admin/** 路径：只有 admin 角色可访问
        if (pathSegments.length > 1 && "admin".equals(pathSegments[1])) {
            if (!"admin".equals(role)) {
                log.warn("非管理员用户尝试访问管理员路径，拒绝访问: {}，用户角色: {}", requestURI, role);
                throw CustomException.error("权限不足");
            }
            log.debug("管理员级别路径访问通过: {}，用户角色: {}", requestURI, role);
        }

        log.info("权限校验通过，请求路径: {}，用户角色: {}", requestURI, role);
        return true;
    }

}