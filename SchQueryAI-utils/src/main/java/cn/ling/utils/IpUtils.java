package cn.ling.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * IP工具类
 * 用于获取客户端真实IP地址
 */
public class IpUtils {

    private static final String UNKNOWN = "unknown";
    private static final String LOCALHOST_IPV4 = "127.0.0.1";
    private static final String LOCALHOST_IPV6 = "0:0:0:0:0:0:0:1";
    private static final int IP_MAX_LENGTH = 15;

    /**
     * 获取客户端真实IP地址
     * 
     * @param request HTTP请求对象
     * @return 客户端IP地址
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }

        String ip = null;

        // 优先从X-Forwarded-For获取（适用于经过代理的情况）
        ip = request.getHeader("X-Forwarded-For");
        if (isValidIp(ip)) {
            // X-Forwarded-For可能包含多个IP，取第一个
            int index = ip.indexOf(',');
            if (index != -1) {
                return ip.substring(0, index).trim();
            }
            return ip.trim();
        }

        // 从Proxy-Client-IP获取
        ip = request.getHeader("Proxy-Client-IP");
        if (isValidIp(ip)) {
            return ip.trim();
        }

        // 从WL-Proxy-Client-IP获取（WebLogic）
        ip = request.getHeader("WL-Proxy-Client-IP");
        if (isValidIp(ip)) {
            return ip.trim();
        }

        // 从HTTP_CLIENT_IP获取
        ip = request.getHeader("HTTP_CLIENT_IP");
        if (isValidIp(ip)) {
            return ip.trim();
        }

        // 从HTTP_X_FORWARDED_FOR获取
        ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        if (isValidIp(ip)) {
            return ip.trim();
        }

        // 从X-Real-IP获取（Nginx代理）
        ip = request.getHeader("X-Real-IP");
        if (isValidIp(ip)) {
            return ip.trim();
        }

        // 最后从RemoteAddr获取
        ip = request.getRemoteAddr();
        
        // 处理IPv6本地地址
        if (LOCALHOST_IPV6.equals(ip)) {
            return LOCALHOST_IPV4;
        }

        return ip != null ? ip.trim() : UNKNOWN;
    }

    /**
     * 判断IP是否有效
     * 
     * @param ip IP地址
     * @return 是否有效
     */
    private static boolean isValidIp(String ip) {
        return StringUtils.hasText(ip) && !UNKNOWN.equalsIgnoreCase(ip);
    }

    /**
     * 判断是否为内网IP
     * 
     * @param ip IP地址
     * @return 是否为内网IP
     */
    public static boolean isInternalIp(String ip) {
        if (!StringUtils.hasText(ip) || UNKNOWN.equalsIgnoreCase(ip)) {
            return false;
        }

        // IPv6本地地址
        if (LOCALHOST_IPV6.equals(ip)) {
            return true;
        }

        // IPv4本地地址
        if (LOCALHOST_IPV4.equals(ip)) {
            return true;
        }

        // 内网IP段
        byte[] addr = textToNumericFormatV4(ip);
        if (addr == null) {
            return false;
        }

        final byte b0 = addr[0];
        final byte b1 = addr[1];

        // 10.x.x.x
        final byte SECTION_1 = 0x0A;
        // 172.16.x.x ~ 172.31.x.x
        final byte SECTION_2 = (byte) 0xAC;
        final byte SECTION_3 = (byte) 0x10;
        final byte SECTION_4 = (byte) 0x1F;
        // 192.168.x.x
        final byte SECTION_5 = (byte) 0xC0;
        final byte SECTION_6 = (byte) 0xA8;

        switch (b0) {
            case SECTION_1:
                return true;
            case SECTION_2:
                if (b1 >= SECTION_3 && b1 <= SECTION_4) {
                    return true;
                }
            case SECTION_5:
                if (b1 == SECTION_6) {
                    return true;
                }
            default:
                return false;
        }
    }

    /**
     * 将IPv4地址转换为字节数组
     * 
     * @param text IPv4地址字符串
     * @return 字节数组
     */
    private static byte[] textToNumericFormatV4(String text) {
        if (text.length() == 0 || text.length() > IP_MAX_LENGTH) {
            return null;
        }

        String[] parts = text.split("\\.");
        if (parts.length != 4) {
            return null;
        }

        byte[] bytes = new byte[4];
        try {
            for (int i = 0; i < 4; i++) {
                long l = Long.parseLong(parts[i]);
                if (l < 0 || l > 255) {
                    return null;
                }
                bytes[i] = (byte) l;
            }
        } catch (NumberFormatException e) {
            return null;
        }

        return bytes;
    }
}
