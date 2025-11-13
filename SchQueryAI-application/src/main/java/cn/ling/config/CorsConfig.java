package cn.ling.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域资源共享配置类
 * 配置CORS（Cross-Origin Resource Sharing）策略，允许前端应用跨域访问后端API
 * 解决浏览器同源策略导致的跨域访问问题
 */
@Slf4j
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * 配置跨域映射规则
     * 设置允许的跨域请求路径、来源域名、HTTP方法等
     *
     * @param registry CORS注册器，用于添加跨域配置
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        log.info("正在配置CORS跨域策略");

        // 配置跨域规则
        registry.addMapping("/**")  // 允许所有路径
                .allowedOriginPatterns("*")  // 允许所有来源域名（生产环境建议配置具体域名）
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")  // 允许的HTTP方法
                .allowedHeaders("*")  // 允许所有请求头
                .allowCredentials(true)  // 允许发送Cookie和认证信息
                .maxAge(3600);  // 预检请求的缓存时间为1小时（3600秒）

        log.info("CORS跨域策略配置完成 - 允许所有路径、所有来源、标准HTTP方法");
    }
}
