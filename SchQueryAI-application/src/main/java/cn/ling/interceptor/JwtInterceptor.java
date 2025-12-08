package cn.ling.interceptor;

import cn.ling.utils.ContextUtils;
import cn.ling.exception.CustomException;
import cn.ling.utils.IpUtils;
import cn.ling.utils.JwtUtils;
import cn.ling.role.UserInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Collections;
import java.util.HashMap;

/**
 * JWT令牌校验拦截器
 * 负责拦截需要认证的请求，验证JWT令牌的有效性
 * 支持用户强制下线功能和令牌黑名单机制
 */
@Slf4j // 启用SLF4J日志功能
public class JwtInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String TOKEN_BLACKLIST_KEY = "token_blacklist:";
    private static final long TOKEN_BLACKLIST_TTL = 2 * 60 * 60; // 2小时，与JWT过期时间一致

    private static final String LOGIN_RATE_LIMIT_KEY_PREFIX = "login_rate_limit:";
    private static final long LOGIN_RATE_LIMIT_WINDOW_MILLIS = 60 * 1000L;
    private static final long LOGIN_RATE_LIMIT_WINDOW_SECONDS = 60L;
    private static final int LOGIN_RATE_LIMIT_THRESHOLD = 1000;
    private static final long LOGIN_RATE_LIMIT_KEY_TTL_SECONDS = LOGIN_RATE_LIMIT_WINDOW_SECONDS * 2;

    private static final String LUA_SLIDING_WINDOW_SCRIPT =
            "local key = KEYS[1]\n" +
            "local now = tonumber(ARGV[1])\n" +
            "local window = tonumber(ARGV[2])\n" +
            "local limit = tonumber(ARGV[3])\n" +
            "local ttl = tonumber(ARGV[4])\n" +
            "redis.call('ZREMRANGEBYSCORE', key, 0, now - window)\n" +
            "local current = redis.call('ZCARD', key)\n" +
            "if current >= limit then\n" +
            "    return -1\n" +
            "end\n" +
            "redis.call('ZADD', key, now, now)\n" +
            "redis.call('EXPIRE', key, ttl)\n" +
            "return current + 1";
    private static final RedisScript<Long> LOGIN_RATE_LIMIT_SCRIPT =
            new DefaultRedisScript<>(LUA_SLIDING_WINDOW_SCRIPT, Long.class);

    public JwtInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 请求处理前执行（拦截请求）
     * 验证JWT令牌的有效性，包括格式校验、签名验证、黑名单检查等
     *
     * @param request HTTP请求对象
     * @param response HTTP响应对象
     * @param handler 处理器对象
     * @return true：通过拦截（继续处理请求）；false：拦截失败（不继续处理）
     * @throws Exception 当令牌验证失败时抛出异常
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestUri = request.getRequestURI();
        String method = request.getMethod();
        log.debug("开始JWT令牌校验，请求路径: {} {}", method, requestUri);

        // 跳过OPTIONS请求（CORS预检请求）
        if ("OPTIONS".equals(method)) {
            log.debug("跳过OPTIONS请求: {}", requestUri);
            return true;
        }

        // 1. 从请求头中获取Authorization字段（JWT令牌通常放在这里）
        // 格式一般为：Authorization: Bearer <token>
        String authHeader = request.getHeader("Authorization");
        log.debug("获取到Authorization头: {}", authHeader != null ? "Bearer *****" : "null");

        // 2. 检查令牌是否存在及格式是否正确
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("令牌格式错误或不存在，请求路径: {}, Authorization: {}", requestUri, authHeader);
            // 令牌不存在或格式错误：返回401未授权
            handleError(response, "未提供有效的令牌");
        }

        // 3. 提取令牌（去掉"Bearer "前缀）
        assert authHeader != null;
        String token = authHeader.substring(7).trim();
        log.debug("提取到JWT令牌，长度: {} 字符", token.length());

        try {
            // 4. 校验令牌有效性（调用JwtUtils解析令牌）
            HashMap<String, Object> allClaimsAsMap = JwtUtils.getAllClaimsAsMap(token);
            log.debug("JWT令牌解析成功");

            // 5. 检查该用户是否被强制下线（密码重置等操作）
            Long userId = Long.parseLong(allClaimsAsMap.get("userId").toString());
            String username = allClaimsAsMap.get("userName").toString();
            String userBlacklistKey = TOKEN_BLACKLIST_KEY + userId;

            if (stringRedisTemplate.hasKey(userBlacklistKey)) {
                log.debug("用户{}在黑名单中，检查令牌签发时间", userId);

                // 检查当前令牌是否在用户黑名单时间之后签发的
                String blacklistedTime = stringRedisTemplate.opsForValue().get(userBlacklistKey);
                long tokenIssuedAt = ((Number) allClaimsAsMap.get("iat")).longValue();
                assert blacklistedTime != null;
                long blacklistedTimestamp = Long.parseLong(blacklistedTime);

                if (tokenIssuedAt < blacklistedTimestamp) {
                    log.warn("用户{}的令牌在强制下线前签发，拒绝访问，请求路径: {}", userId, requestUri);
                    handleError(response, "用户信息已更新，请重新登录");
                    return false;
                }
                log.debug("用户{}的令牌在强制下线后签发，允许访问", userId);
            }

            //设置限流
            enforceLoginRateLimit(userId, username, request, requestUri, response);

            // 6. 令牌有效：将用户信息存入上下文（方便后续Controller获取）
            UserInfo userInfo = new UserInfo();
            userInfo.setUserId(userId);
            userInfo.setUsername(username);
            userInfo.setRole(allClaimsAsMap.get("role").toString());
            ContextUtils.setUserInfo(userInfo);

            log.info("JWT令牌校验成功，用户ID: {}, 用户名: {}, 角色: {}, 请求路径: {}",
                    userId, username, userInfo.getRole(), requestUri);

            // 7. 放行请求
            return true;

        } catch (Exception e) {
            // 校验过程中发生异常（如签名错误、格式错误等）
            String errorMessage = e.getMessage();
            log.warn("JWT令牌校验失败，请求路径: {}, 错误信息: {}", requestUri, errorMessage);

            // 检查是否是令牌过期
            if (errorMessage != null && errorMessage.contains("JWT expired")) {
                handleError(response, "TOKEN_EXPIRED");
            } else {
                handleError(response, "令牌校验失败：" + errorMessage);
            }
            return false;
        }
    }

    private void enforceLoginRateLimit(Long userId, String username, HttpServletRequest request,
                                       String requestUri, HttpServletResponse response) throws Exception {
        String clientIp = IpUtils.getClientIp(request);
        if (clientIp == null) {
            clientIp = "unknown";
        } else {
            clientIp = clientIp.trim();
            if (clientIp.isEmpty()) {
                clientIp = "unknown";
            }
        }

        String rateLimitKey = LOGIN_RATE_LIMIT_KEY_PREFIX + userId + ":" + clientIp;
        long now = System.currentTimeMillis();

        Long scriptResult = stringRedisTemplate.execute(
                LOGIN_RATE_LIMIT_SCRIPT,
                Collections.singletonList(rateLimitKey),
                String.valueOf(now),
                String.valueOf(LOGIN_RATE_LIMIT_WINDOW_MILLIS),
                String.valueOf(LOGIN_RATE_LIMIT_THRESHOLD),
                String.valueOf(LOGIN_RATE_LIMIT_KEY_TTL_SECONDS)
        );

        if (scriptResult == null) {
            log.warn("执行Redis限流脚本失败，允许请求通过，key: {}", rateLimitKey);
            return;
        }

        if (scriptResult == -1L) {
            log.warn("用户{}({})在IP {}上一分钟内访问次数超过限制，请求路径: {}", username, userId, clientIp, requestUri);
            handleError(response, "用户访问过于频繁，请稍后再试");
            return;
        }

        log.debug("刷新访问窗口，用户{}({})在IP {}上的计数: {}", username, userId, clientIp, scriptResult);
    }

    /**
     * 处理拦截失败的响应（返回统一格式的错误信息）
     *
     * @param response HTTP响应对象
     * @param message 错误信息
     * @throws Exception 抛出业务异常
     */
    private void handleError(HttpServletResponse response, String message) throws Exception {
        log.debug("JWT拦截处理失败，错误信息: {}", message);
        throw new CustomException(401, message);
    }

    /**
     * 请求处理完成后执行（清理上下文）
     * 无论请求成功还是失败，都会执行此方法清理用户上下文信息
     *
     * @param request HTTP请求对象
     * @param response HTTP响应对象
     * @param handler 处理器对象
     * @param ex 异常对象（如果有的话）
     * @throws Exception 清理过程中可能发生的异常
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        log.debug("清理用户上下文信息，请求路径: {}", request.getRequestURI());
        ContextUtils.clear();
    }
}
