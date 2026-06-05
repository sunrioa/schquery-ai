package cn.ling.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类：生成令牌、获取所有载荷（返回HashMap）
 * 提供JWT（JSON Web Token）的生成、解析和验证功能，用于系统身份认证和授权
 * 支持HMAC-SHA256签名算法，设置合理的过期时间，确保令牌安全性
 */
@Slf4j
@Component
public class JwtUtils {

    private static final String DEFAULT_SECRET_KEY = "change-this-jwt-secret-key-at-least-32-bytes";
    private static final String JWT_SECRET_ENV = "JWT_SECRET_KEY";

    // 令牌过期时间（2小时，单位：毫秒）
    private static final long EXPIRATION_TIME = 2 * 60 * 60 * 1000;

    /**
     * 生成JWT令牌
     * 使用HMAC-SHA256算法创建签名的JWT令牌，包含用户信息和自定义载荷
     *
     * @param subject 主题（通常是用户名或用户ID，用于唯一标识用户）
     * @param claims 自定义载荷（如用户角色、权限、额外信息等）
     * @return 生成的JWT令牌字符串
     * @throws IllegalArgumentException 当subject为null或空时抛出异常
     */
    public static String generateToken(String subject, Map<String, Object> claims) {
        log.info("开始生成JWT令牌，主题: {}", subject);
        log.debug("自定义载荷数量: {}", claims != null ? claims.size() : 0);

        try {
            // 参数校验
            if (subject == null || subject.trim().isEmpty()) {
                log.error("生成JWT令牌失败：主题不能为空");
                throw new IllegalArgumentException("主题不能为空");
            }

            if (claims == null) {
                log.debug("自定义载荷为null，使用空的载荷");
                claims = new HashMap<>();
            }

            // 生成签名密钥（HMAC-SHA256需要256位密钥）
            log.debug("生成HMAC-SHA256签名密钥");
            SecretKey key = getSecretKey();

            // 计算过期时间
            Date expirationTime = new Date(System.currentTimeMillis() + EXPIRATION_TIME);
            Date issueTime = new Date();

            log.debug("JWT令牌配置 - 签发时间: {}, 过期时间: {}, 有效期: {} 小时",
                issueTime, expirationTime, EXPIRATION_TIME / (60 * 60 * 1000));

            // 构建令牌
            String token = Jwts.builder()
                    .setClaims(claims) // 自定义载荷
                    .setSubject(subject) // 主题（唯一标识，如用户ID）
                    .setIssuedAt(issueTime) // 签发时间
                    .setExpiration(expirationTime) // 过期时间
                    .signWith(key, SignatureAlgorithm.HS256) // 签名算法和密钥
                    .compact();

            log.info("JWT令牌生成成功，主题: {}, 令牌长度: {}", subject, token.length());
            log.debug("生成的JWT令牌前缀: {}", token.substring(0, Math.min(20, token.length())) + "...");

            return token;
        } catch (Exception e) {
            log.error("生成JWT令牌时发生异常，主题: {}, 异常信息: {}", subject, e.getMessage(), e);
            throw new RuntimeException("生成JWT令牌失败: " + e.getMessage(), e);
        }
    }


    /**
     * 获取令牌中的所有载荷（返回HashMap）
     * 解析JWT令牌并验证签名有效性，返回包含所有声明信息的Map对象
     * 包括标准声明（如iss、sub、exp等）和自定义声明
     *
     * @param token JWT令牌字符串
     * @return 包含所有载荷的HashMap（包括标准声明和自定义声明）
     * @throws JwtException 令牌无效、过期、签名错误等情况会抛出异常
     * @throws IllegalArgumentException 当token为null或空时抛出异常
     */
    public static HashMap<String, Object> getAllClaimsAsMap(String token) {
        log.info("开始解析JWT令牌");
        log.debug("JWT令牌前缀: {}", token != null && token.length() > 20 ?
            token.substring(0, 20) + "..." : token);

        try {
            // 参数校验
            if (token == null || token.trim().isEmpty()) {
                log.error("解析JWT令牌失败：令牌不能为空");
                throw new IllegalArgumentException("JWT令牌不能为空");
            }

            // 生成签名密钥
            log.debug("生成JWT解析密钥");
            SecretKey key = getSecretKey();

            // 解析并验证JWT令牌
            log.debug("开始解析JWT令牌并验证签名");
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            // 检查令牌是否过期
            Date expiration = claims.getExpiration();
            Date now = new Date();
            if (expiration != null && expiration.before(now)) {
                log.warn("JWT令牌已过期 - 过期时间: {}, 当前时间: {}", expiration, now);
                throw new ExpiredJwtException(null, claims, "JWT令牌已过期");
            }

            log.info("JWT令牌解析成功，主题: {}, 过期时间: {}", claims.getSubject(), expiration);
            log.debug("令牌声明数量: {}", claims.size());

            // 将Claims转换为HashMap（Claims本身是Map的实现类，直接转换即可）
            return new HashMap<>(claims);

        } catch (ExpiredJwtException e) {
            log.error("JWT令牌已过期: {}", e.getMessage());
            throw e;
        } catch (UnsupportedJwtException e) {
            log.error("不支持的JWT令牌格式: {}", e.getMessage());
            throw e;
        } catch (MalformedJwtException e) {
            log.error("JWT令牌格式错误: {}", e.getMessage());
            throw e;
        } catch (SecurityException e) {
            log.error("JWT令牌签名验证失败: {}", e.getMessage());
            throw e;
        } catch (IllegalArgumentException e) {
            log.error("JWT令牌参数错误: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("解析JWT令牌时发生未知异常: {}", e.getMessage(), e);
            throw new RuntimeException("解析JWT令牌失败: " + e.getMessage(), e);
        }
    }

    private static SecretKey getSecretKey() {
        String secret = System.getProperty(JWT_SECRET_ENV);
        if (secret == null || secret.trim().isEmpty()) {
            secret = System.getenv(JWT_SECRET_ENV);
        }
        if (secret == null || secret.trim().isEmpty()) {
            secret = DEFAULT_SECRET_KEY;
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET_KEY长度不能少于32字节");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
