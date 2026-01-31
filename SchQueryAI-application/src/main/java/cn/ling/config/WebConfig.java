package cn.ling.config;

import cn.ling.interceptor.JwtInterceptor;
import cn.ling.interceptor.PowerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置类
 * 负责注册和配置应用程序的拦截器，包括JWT认证拦截器和权限校验拦截器
 * 配置拦截路径规则和豁免路径列表
 */
@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 注册拦截器并配置拦截规则
     * 配置JWT认证拦截器和权限校验拦截器的路径匹配规则
     *
     * @param registry 拦截器注册器，用于添加和配置拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("开始注册Web拦截器");

        // 定义不需要认证的公共路径
        String[] excludePatterns = {
                "/user/login",                    // 用户登录接口
                "/user/register",                 // 用户注册接口
                "/user/sendRegisterCode",         // 发送注册验证码
                "/user/sendFindPasswordCode",     // 发送找回密码验证码// 找回密码接口
                "/user/findPassword",             // 找回密码接口
                "/widget/auth",                   // 外部Widget鉴权
                "/widget/config",                 // 外部Widget配置
                "/widget/**",                     // 外部Widget全部接口兜底
                "/api/widget/**",                 // 兼容前端直接带/api的请求
                "/ws/**"                          // WebSocket 握手无需 JWT
        };
        log.debug("配置公共访问路径: {}", (Object) excludePatterns);

        // 1. 注册JWT认证拦截器
        // 负责验证用户身份令牌的有效性
        registry.addInterceptor(new JwtInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")          // 拦截所有请求路径
                .excludePathPatterns(excludePatterns); // 排除公共路径

        log.info("JWT认证拦截器注册完成 - 拦截所有路径，排除公共访问接口");

        // 2. 注册权限校验拦截器
        // 负责根据用户角色进行权限验证
        registry.addInterceptor(new PowerInterceptor())
                .addPathPatterns("/**")          // 拦截所有请求路径
                .excludePathPatterns(excludePatterns); // 排除公共路径

        log.info("权限校验拦截器注册完成 - 拦截所有路径，排除公共访问接口");
        log.info("所有Web拦截器注册完成");
    }
}
