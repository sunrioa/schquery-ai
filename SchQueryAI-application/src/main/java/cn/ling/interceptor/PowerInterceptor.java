package cn.ling.interceptor;


import cn.ling.context.ContextUtils;
import cn.ling.exception.CustomException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

public class PowerInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 跳过OPTIONS请求（CORS预检请求）
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        String requestURI = request.getRequestURI();
        String[] split = requestURI.split("/");

        // 分割后，split[0]是空字符串，split[1]是第一个路径段
        if (split.length > 1 && "user".equals(split[1])) {
            return true; // 所有/user/**路径直接放行
        }

        String role = ContextUtils.getUserInfo().getRole();

        if (split.length > 1 && "worker".equals(split[1])) {
            if ("user".equals(role)) {
                throw CustomException.error("权限不足");
            }
        }

        if (split.length > 1 && "admin".equals(split[1])) {
            if (!"admin".equals(role)) {
                throw CustomException.error("权限不足");
            }
        }

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}