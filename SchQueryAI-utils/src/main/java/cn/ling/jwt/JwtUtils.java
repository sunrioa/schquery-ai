package cn.ling.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类：生成令牌、获取所有载荷（返回HashMap）
 */
public class JwtUtils {

    // 密钥（实际项目中建议从配置文件读取，长度至少32位）
    private static final String SECRET_KEY = "${JWT_SECRET_KEY}";

    // 令牌过期时间（2小时，单位：毫秒）
    private static final long EXPIRATION_TIME = 2 * 60 * 60 * 1000;

    /**
     * 生成JWT令牌
     * @param subject 主题（通常是用户名/用户ID）
     * @param claims 自定义载荷（如角色、权限等）
     * @return 生成的令牌
     */
    public static String generateToken(String subject, Map<String, Object> claims) {
        // 生成签名密钥（HMAC-SHA256需要256位密钥）
        SecretKey key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));

        // 构建令牌
        return Jwts.builder()
                .setClaims(claims) // 自定义载荷
                .setSubject(subject) // 主题（唯一标识，如用户ID）
                .setIssuedAt(new Date()) // 签发时间
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME)) // 过期时间
                .signWith(key, SignatureAlgorithm.HS256) // 签名算法和密钥
                .compact();
    }


    /**
     * 获取令牌中的所有载荷（返回HashMap）
     * @param token JWT令牌
     * @return 包含所有载荷的HashMap（包括标准声明和自定义声明）
     * @throws JwtException 令牌无效、过期、签名错误等情况会抛出异常
     */
    public static HashMap<String, Object> getAllClaimsAsMap(String token) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        // 将Claims转换为HashMap（Claims本身是Map的实现类，直接转换即可）
        return new HashMap<>(claims);
    }
}