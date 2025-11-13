package cn.ling.utils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * IP地址工具类
 * 提供获取客户端真实IP地址的功能，支持多种代理和负载均衡环境
 * 能够识别和处理各种HTTP头部信息中的IP地址，包括X-Forwarded-For、X-Real-IP等
 * 同时提供内网IP地址判断功能，用于网络安全和访问控制
 */
@Slf4j
public class IpUtils {

    private static final String UNKNOWN = "unknown";
    private static final String LOCALHOST_IPV4 = "127.0.0.1";
    private static final String LOCALHOST_IPV6 = "0:0:0:0:0:0:0:1";
    private static final int IP_MAX_LENGTH = 15;

    /**
     * 获取客户端真实IP地址
     * 通过检查HTTP请求头中的多个可能的IP地址字段来获取真实的客户端IP
     * 按照优先级顺序检查：X-Forwarded-For -> Proxy-Client-IP -> WL-Proxy-Client-IP -> HTTP_CLIENT_IP -> HTTP_X_FORWARDED_FOR -> X-Real-IP -> RemoteAddr
     * 支持多级代理环境，能够正确处理代理服务器转发的IP地址信息
     *
     * @param request HTTP请求对象，包含客户端请求信息
     * @return 客户端真实IP地址；如果无法获取则返回"unknown"
     */
    public static String getClientIp(HttpServletRequest request) {
        log.debug("开始获取客户端真实IP地址");

        // 参数验证
        if (request == null) {
            log.warn("HTTP请求对象为null，无法获取客户端IP");
            return UNKNOWN;
        }

        String ip;

        try {
            // 优先从X-Forwarded-For获取（适用于经过多层代理的情况）
            log.debug("检查X-Forwarded-For头部");
            ip = request.getHeader("X-Forwarded-For");
            if (isValidIp(ip)) {
                log.debug("从X-Forwarded-For获取到IP: {}", ip);
                // X-Forwarded-For可能包含多个IP，格式为：client, proxy1, proxy2，取第一个客户端IP
                int index = ip.indexOf(',');
                if (index != -1) {
                    String clientIp = ip.substring(0, index).trim();
                    log.debug("X-Forwarded-For包含多个IP，取第一个客户端IP: {}", clientIp);
                    return clientIp;
                }
                log.debug("获取到客户端IP: {}", ip.trim());
                return ip.trim();
            }

            // 从Proxy-Client-IP获取（Apache代理）
            log.debug("检查Proxy-Client-IP头部");
            ip = request.getHeader("Proxy-Client-IP");
            if (isValidIp(ip)) {
                log.debug("从Proxy-Client-IP获取到IP: {}", ip);
                return ip.trim();
            }

            // 从WL-Proxy-Client-IP获取（WebLogic代理）
            log.debug("检查WL-Proxy-Client-IP头部");
            ip = request.getHeader("WL-Proxy-Client-IP");
            if (isValidIp(ip)) {
                log.debug("从WL-Proxy-Client-IP获取到IP: {}", ip);
                return ip.trim();
            }

            // 从HTTP_CLIENT_IP获取（某些代理服务器的自定义头部）
            log.debug("检查HTTP_CLIENT_IP头部");
            ip = request.getHeader("HTTP_CLIENT_IP");
            if (isValidIp(ip)) {
                log.debug("从HTTP_CLIENT_IP获取到IP: {}", ip);
                return ip.trim();
            }

            // 从HTTP_X_FORWARDED_FOR获取（另一种X-Forwarded-For的写法）
            log.debug("检查HTTP_X_FORWARDED_FOR头部");
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
            if (isValidIp(ip)) {
                log.debug("从HTTP_X_FORWARDED_FOR获取到IP: {}", ip);
                return ip.trim();
            }

            // 从X-Real-IP获取（Nginx代理常用的头部）
            log.debug("检查X-Real-IP头部");
            ip = request.getHeader("X-Real-IP");
            if (isValidIp(ip)) {
                log.debug("从X-Real-IP获取到IP: {}", ip);
                return ip.trim();
            }

            // 最后从RemoteAddr获取（直接连接，无代理情况）
            log.debug("从RemoteAddr获取IP");
            ip = request.getRemoteAddr();
            log.debug("RemoteAddr获取到的IP: {}", ip);

            // 处理IPv6本地地址转换为IPv4格式
            if (LOCALHOST_IPV6.equals(ip)) {
                log.debug("检测到IPv6本地地址，转换为IPv4格式");
                return LOCALHOST_IPV4;
            }

            String result = ip != null ? ip.trim() : UNKNOWN;
            log.info("最终获取到的客户端IP地址: {}", result);
            return result;

        } catch (Exception e) {
            log.error("获取客户端IP地址时发生异常: {}", e.getMessage(), e);
            return UNKNOWN;
        }
    }

    /**
     * 判断IP地址是否有效
     * 检查IP地址字符串是否不为空、不为null且不等于"unknown"
     *
     * @param ip 待验证的IP地址字符串
     * @return IP地址有效返回true，无效返回false
     */
    private static boolean isValidIp(String ip) {
        boolean isValid = StringUtils.hasText(ip) && !UNKNOWN.equalsIgnoreCase(ip);
        log.debug("IP地址有效性验证: {} -> {}", ip, isValid);
        return isValid;
    }

    /**
     * 判断IP地址是否为内网地址
     * 检查给定的IP地址是否属于内网IP段，包括：
     * - 127.0.0.1（IPv4本地回环地址）
     * - 0:0:0:0:0:0:0:1（IPv6本地回环地址）
     * - 10.0.0.0 - 10.255.255.255（A类私有地址）
     * - 172.16.0.0 - 172.31.255.255（B类私有地址）
     * - 192.168.0.0 - 192.168.255.255（C类私有地址）
     *
     * @param ip 待检查的IP地址
     * @return 是内网地址返回true，公网地址返回false
     */
    public static boolean isInternalIp(String ip) {
        log.debug("开始检查IP地址是否为内网地址: {}", ip);

        // 参数验证
        if (!StringUtils.hasText(ip) || UNKNOWN.equalsIgnoreCase(ip)) {
            log.debug("IP地址无效或为unknown，判定为非内网地址");
            return false;
        }

        try {
            // 检查IPv6本地地址
            if (LOCALHOST_IPV6.equals(ip)) {
                log.debug("检测到IPv6本地回环地址: {}", ip);
                return true;
            }

            // 检查IPv4本地地址
            if (LOCALHOST_IPV4.equals(ip)) {
                log.debug("检测到IPv4本地回环地址: {}", ip);
                return true;
            }

            // 将IPv4地址转换为字节数组进行段位判断
            log.debug("开始IPv4地址段位分析");
            byte[] addr = textToNumericFormatV4(ip);
            if (addr == null) {
                log.debug("IPv4地址格式无效: {}", ip);
                return false;
            }

            final byte b0 = addr[0]; // 第一个字节段
            final byte b1 = addr[1]; // 第二个字节段

            // 定义内网IP段的常量
            final byte SECTION_1 = 0x0A; // 10.x.x.x (A类私有地址)
            final byte SECTION_2 = (byte) 0xAC; // 172.x.x.x (B类私有地址前缀)
            final byte SECTION_3 = (byte) 0x10; // 172.16.x.x (B类私有地址起始)
            final byte SECTION_4 = (byte) 0x1F; // 172.31.x.x (B类私有地址结束)
            final byte SECTION_5 = (byte) 0xC0; // 192.x.x.x (C类私有地址前缀)
            final byte SECTION_6 = (byte) 0xA8; // 192.168.x.x (C类私有地址)

            // 根据第一字节段判断内网地址类型
            switch (b0) {
                case SECTION_1:
                    // 10.0.0.0 - 10.255.255.255 (A类私有地址)
                    log.debug("检测到A类私有地址段: 10.x.x.x");
                    return true;
                case SECTION_2:
                    // 172.16.0.0 - 172.31.255.255 (B类私有地址)
                    if (b1 >= SECTION_3 && b1 <= SECTION_4) {
                        log.debug("检测到B类私有地址段: 172.{}.x.x", b1 & 0xFF);
                        return true;
                    }
                    break;
                case SECTION_5:
                    // 192.168.0.0 - 192.168.255.255 (C类私有地址)
                    if (b1 == SECTION_6) {
                        log.debug("检测到C类私有地址段: 192.168.x.x");
                        return true;
                    }
                    break;
                default:
                    log.debug("非内网地址段: {}.{}.x.x", b0 & 0xFF, b1 & 0xFF);
                    return false;
            }

            return false;

        } catch (Exception e) {
            log.error("判断内网IP地址时发生异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 将IPv4地址字符串转换为字节数组
     * 将标准IPv4地址（如192.168.1.1）转换为4字节的数组表示形式
     * 每个字节对应IP地址的一个段，便于后续的字节级别比较和计算
     *
     * @param text IPv4地址字符串，格式为x.x.x.x
     * @return 4字节字节数组，第一个元素对应IP地址的第一段；转换失败返回null
     */
    private static byte[] textToNumericFormatV4(String text) {
        log.debug("开始将IPv4地址转换为字节数组: {}", text);

        // 参数验证：检查长度和格式
        if (text.isEmpty() || text.length() > IP_MAX_LENGTH) {
            log.debug("IPv4地址长度无效: {}", text.length());
            return null;
        }

        // 按点号分割IP地址段
        String[] parts = text.split("\\.");
        if (parts.length != 4) {
            log.debug("IPv4地址段数无效，应为4段，实际为{}段: {}", parts.length, text);
            return null;
        }

        byte[] bytes = new byte[4];
        try {
            // 逐段解析IP地址
            for (int i = 0; i < 4; i++) {
                log.debug("解析IPv4地址第{}段: {}", i + 1, parts[i]);

                // 解析每段的数值
                long l = Long.parseLong(parts[i]);
                if (l < 0 || l > 255) {
                    log.debug("IPv4地址第{}段数值超出有效范围[0-255]: {}", i + 1, l);
                    return null;
                }

                bytes[i] = (byte) l;
                log.debug("第{}段解析完成: {} -> {}", i + 1, l, bytes[i] & 0xFF);
            }

            log.debug("IPv4地址转换成功: {}", text);
            return bytes;

        } catch (NumberFormatException e) {
            log.debug("IPv4地址包含非数字字符: {}", text);
            return null;
        } catch (Exception e) {
            log.error("IPv4地址转换过程中发生异常: {}", e.getMessage(), e);
            return null;
        }
    }
}
