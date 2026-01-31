package cn.ling.controller;

import cn.ling.Result;
import cn.ling.utils.JwtUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 外部网站接入的Widget接口
 * 提供简单的配置获取与临时鉴权token下发
 */
@Slf4j
@RestController
@RequestMapping("/widget")
public class WidgetController {

    @Value("${parameters.widget.siteKey:demo-site-key}")
    private String siteKey;

    @Value("${parameters.widget.title:SchQueryAI}")
    private String title;

    @Value("${parameters.widget.subtitle:AI Assistant}")
    private String subtitle;

    @Value("${parameters.widget.primaryColor:#1d4ed8}")
    private String primaryColor;

    @Value("${parameters.widget.backgroundColor:#ffffff}")
    private String backgroundColor;

    @Value("${parameters.widget.textColor:#0f172a}")
    private String textColor;

    @Value("${parameters.widget.allowedOrigin:https://example.com}")
    private String allowedOrigin;

    @GetMapping("/config")
    public Result<Map<String, Object>> getConfig(@RequestParam(required = false) String siteKey) {
        if (siteKey != null && !siteKey.equals(this.siteKey)) {
            return Result.error(403, "站点未授权");
        }

        Map<String, Object> config = new HashMap<>();
        config.put("title", title);
        config.put("subtitle", subtitle);
        config.put("primaryColor", primaryColor);
        config.put("backgroundColor", backgroundColor);
        config.put("textColor", textColor);
        config.put("allowedOrigin", allowedOrigin);
        return Result.success(config);
    }

    @PostMapping("/auth")
    public Result<Map<String, Object>> auth(@RequestBody WidgetAuthRequest request) {
        if (request == null || request.getSiteKey() == null || !request.getSiteKey().equals(siteKey)) {
            return Result.error(403, "站点未授权");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", 0L);
        claims.put("userName", "widget");
        claims.put("role", "user");
        claims.put("siteKey", request.getSiteKey());

        String token = JwtUtils.generateToken("widget", claims);
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", token);
        return Result.success(payload);
    }

    @Data
    public static class WidgetAuthRequest {
        private String siteKey;
    }
}
