package cn.ling.interceptor;


import cn.ling.context.Context;
import cn.ling.exception.CustomException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

public class PowerInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        String requestURI = request.getRequestURI();
        String[] split = requestURI.split("/");

        if ("user".equals(split[0])) {
            return true;
        }

        String role = Context.getUserInfo().getRole();

        if("worker".equals(split[0])){
            if ("user".equals(role)){
                throw CustomException.error("权限不足");
            }
        }

        if("admin".equals(split[0])){
            if (!"admin".equals(role)){
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
