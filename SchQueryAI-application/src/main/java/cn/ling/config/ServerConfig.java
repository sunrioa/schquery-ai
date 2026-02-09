package cn.ling.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServerConfig {

    @Bean
    public WebServerFactoryCustomizer<ConfigurableWebServerFactory> webServerFactoryCustomizer() {
        return factory -> {
            if (factory instanceof TomcatServletWebServerFactory) {
                TomcatServletWebServerFactory tomcatFactory = (TomcatServletWebServerFactory) factory;
                
                // 设置连接超时时间，防止连接被意外关闭
                tomcatFactory.addConnectorCustomizers(connector -> {
                    connector.setProperty("connectionTimeout", "20000");
                    // 设置保持连接状态下的最大请求数
                    connector.setProperty("maxKeepAliveRequests", "100");
                    // 设置保持连接的超时时间
                    connector.setProperty("keepAliveTimeout", "20000");
                    // 设置最大连接数
                    connector.setProperty("maxConnections", "8192");
                    // 设置接收队列大小
                    connector.setProperty("acceptCount", "100");

                    // 关键配置：禁用上传超时，保持长连接
                    connector.setProperty("disableUploadTimeout", "true");
                    // 设置较小的缓冲区大小，减少 SSE 响应缓冲延迟
                    connector.setProperty("socket.appreadbufsize", "2048");
                    connector.setProperty("socket.appwritebufsize", "2048");
                });
            }
        };
    }
}