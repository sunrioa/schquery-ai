package cn.ling.config;

import cn.ling.handler.CustomerServiceWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final CustomerServiceWebSocketHandler customerServiceWebSocketHandler;

    public WebSocketConfig(CustomerServiceWebSocketHandler customerServiceWebSocketHandler) {
        this.customerServiceWebSocketHandler = customerServiceWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册客服 WebSocket 端点
        // 连接 URL: ws://localhost:8001/ws/customer-service?userId=123&userType=user
        registry.addHandler(customerServiceWebSocketHandler, "/ws/customer-service")
                .setAllowedOrigins("*");
    }
}
