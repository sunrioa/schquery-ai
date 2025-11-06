package cn.ling.interceptor;

import cn.ling.utils.ContextUtils;
import cn.ling.exception.CustomException;
import cn.ling.utils.JwtUtils;
import cn.ling.role.UserInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashMap;

/**
 * JWT令牌校验拦截器
 */
public class JwtInterceptor implements HandlerInterceptor {

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
            // 4. 校验令牌有效性（调用之前的JwtUtils）
            HashMap<String, Object> allClaimsAsMap = JwtUtils.getAllClaimsAsMap(token);
            // 5. 令牌有效：可以将用户信息存入请求属性（方便后续Controller获取）
            UserInfo userInfo = new UserInfo();
            userInfo.setUserId(Long.parseLong(allClaimsAsMap.get("userId").toString()));
            userInfo.setUsername(allClaimsAsMap.get("userName").toString());
            userInfo.setRole(allClaimsAsMap.get("role").toString());
            ContextUtils.setUserInfo(userInfo);

            // 6. 放行请求
            return true;

        } catch (Exception e) {
            // 校验过程中发生异常（如签名错误、格式错误等）
            handleError(response, "令牌校验失败：" + e.getMessage());
        }

        return true;
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
