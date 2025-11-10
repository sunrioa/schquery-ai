package cn.ling.interceptor;

import cn.ling.utils.ContextUtils;
import cn.ling.exception.CustomException;
import cn.ling.utils.JwtUtils;
import cn.ling.role.UserInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashMap;

/**
 * JWT令牌校验拦截器
 */
public class JwtInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    private static final String TOKEN_BLACKLIST_KEY = "token_blacklist:";
    private static final long TOKEN_BLACKLIST_TTL = 2 * 60 * 60; // 2小时，与JWT过期时间一致

    public JwtInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 请求处理前执行（拦截请求）
     * @return true：通过拦截（继续处理请求）；false：拦截失败（不继续处理）
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 跳过OPTIONS请求（CORS预检请求）
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        // 1. 从请求头中获取Authorization字段（JWT令牌通常放在这里）
        // 格式一般为：Authorization: Bearer <token>
        String authHeader = request.getHeader("Authorization");

        // 2. 检查令牌是否存在及格式是否正确
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // 令牌不存在或格式错误：返回401未授权
            handleError(response, "未提供有效的令牌");
        }

        // 3. 提取令牌（去掉"Bearer "前缀）
        String token = authHeader.substring(7).trim();

        try {
            // 5. 校验令牌有效性（调用之前的JwtUtils）
            HashMap<String, Object> allClaimsAsMap = JwtUtils.getAllClaimsAsMap(token);

            // 6. 检查该用户是否被强制下线（密码重置等操作）
            Long userId = Long.parseLong(allClaimsAsMap.get("userId").toString());
            String userBlacklistKey = TOKEN_BLACKLIST_KEY + userId;

            if (stringRedisTemplate.hasKey(userBlacklistKey)) {
                // 检查当前令牌是否在用户黑名单时间之后签发的
                String blacklistedTime = stringRedisTemplate.opsForValue().get(userBlacklistKey);
                long tokenIssuedAt = ((Number) allClaimsAsMap.get("iat")).longValue();
                long blacklistedTimestamp = Long.parseLong(blacklistedTime);

                if (tokenIssuedAt < blacklistedTimestamp) {
                    handleError(response, "用户信息已更新，请重新登录");
                    return false;
                }
            }

            // 7. 令牌有效：可以将用户信息存入请求属性（方便后续Controller获取）
            UserInfo userInfo = new UserInfo();
            userInfo.setUserId(userId);
            userInfo.setUsername(allClaimsAsMap.get("userName").toString());
            userInfo.setRole(allClaimsAsMap.get("role").toString());
            ContextUtils.setUserInfo(userInfo);

            // 8. 放行请求
            return true;

              } catch (Exception e) {
            // 校验过程中发生异常（如签名错误、格式错误等）
            String errorMessage = e.getMessage();

            // 检查是否是令牌过期
            if (errorMessage != null && errorMessage.contains("JWT expired")) {
                handleError(response, "TOKEN_EXPIRED");
            } else {
                handleError(response, "令牌校验失败：" + errorMessage);
            }
        }

        return false; // 令牌验证失败，不应该继续处理请求
    }

    /**
     * 处理拦截失败的响应（返回统一格式的错误信息）
     */
    private void handleError(HttpServletResponse response, String message) throws Exception {
        throw new CustomException(401, message);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        ContextUtils.clear();
    }
}
