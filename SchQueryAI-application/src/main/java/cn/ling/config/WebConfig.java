package cn.ling.config;

import cn.ling.interceptor.JwtInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置：注册拦截器并配置拦截规则
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册JWT拦截器
        registry.addInterceptor(new JwtInterceptor())
                // 拦截所有路径（/** 表示所有请求）
                .addPathPatterns("/**")
                // 排除不需要拦截的路径（根据实际业务调整）
                .excludePathPatterns(
                        "/login", // 登录接口
                        "/register" // 注册接口
                );
    }
}
