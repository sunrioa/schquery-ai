package cn.ling.utils;

import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;

/**
 * BCrypt密码加密工具类
 * 提供基于BCrypt算法的密码加密和验证功能
 * BCrypt是一种基于Blowfish的密码哈希算法，专门为密码存储设计，内置盐值生成
 * 具有抗彩虹表攻击、计算强度可调节、安全性高的特点
 */
@Slf4j
public class BCryptUtils {

    /**
     * 对明文密码进行BCrypt加密
     * 自动生成随机盐值并使用BCrypt算法对密码进行哈希加密
     * 每次加密都会生成不同的盐值，确保相同密码产生不同的哈希结果
     *
     * @param password 需要加密的明文密码
     * @return 加密后的密码哈希字符串，包含盐值信息；如果输入为null则返回null
     * @throws IllegalArgumentException 当密码为空字符串时抛出异常
     */
    public static String encode(String password) {
        log.debug("开始对密码进行BCrypt加密");

        // 参数验证
        if (password == null) {
            log.warn("密码为null，无法进行加密");
            return null;
        }

        if (password.trim().isEmpty()) {
            log.error("密码为空字符串，不允许加密空密码");
            throw new IllegalArgumentException("密码不能为空");
        }

        try {
            log.debug("生成随机盐值并进行BCrypt哈希加密，密码长度: {}", password.length());

            // 使用BCrypt进行密码哈希加密，自动生成盐值
            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

            log.info("密码BCrypt加密成功，输出哈希长度: {}字符", hashedPassword.length());
            log.debug("生成的密码哈希前缀: {}", hashedPassword.substring(0, Math.min(10, hashedPassword.length())));

            return hashedPassword;

        } catch (Exception e) {
            log.error("密码BCrypt加密失败: {}", e.getMessage(), e);
            throw new RuntimeException("密码加密失败", e);
        }
    }

    /**
     * 验证明文密码与BCrypt哈希密码是否匹配
     * 通过BCrypt算法验证明文密码是否与存储的哈希密码对应
     * 从哈希密码中提取盐值，对明文密码进行相同的哈希计算后比较结果
     *
     * @param password 待验证的明文密码
     * @param hashedPassword 已存储的BCrypt哈希密码
     * @return 验证成功返回true，密码不匹配返回false；参数无效时返回false
     */
    public static Boolean judge(String password, String hashedPassword) {
        log.debug("开始验证密码匹配性");

        // 参数验证
        if (password == null || hashedPassword == null) {
            log.warn("密码或哈希密码为null，验证失败 - 密码是否为null: {}, 哈希密码是否为null: {}",
                    password == null, hashedPassword == null);
            return false;
        }

        if (password.trim().isEmpty()) {
            log.warn("明文密码为空字符串，验证失败");
            return false;
        }

        if (hashedPassword.trim().isEmpty()) {
            log.warn("哈希密码为空字符串，验证失败");
            return false;
        }

        try {
            log.debug("开始BCrypt密码验证，明文密码长度: {}, 哈希密码长度: {}",
                    password.length(), hashedPassword.length());

            // 使用BCrypt验证密码
            boolean isMatch = BCrypt.checkpw(password, hashedPassword);

            if (isMatch) {
                log.info("密码验证成功");
            } else {
                log.warn("密码验证失败，密码不匹配");
            }

            return isMatch;

        } catch (Exception e) {
            log.error("密码验证过程中发生异常: {}", e.getMessage(), e);
            return false;
        }
    }
}
