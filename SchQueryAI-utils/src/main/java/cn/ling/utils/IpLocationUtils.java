package cn.ling.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * IP地理位置工具类
 * 支持多种IP地理位置查询API
 */
@Slf4j
public class IpLocationUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int TIMEOUT = 5000; // 5秒超时

    /**
     * IP地理位置信息
     */
    @Getter
    @Setter
    public static class LocationInfo {
        private String country = "未知";
        private String province = "未知";
        private String city = "未知";
        private String isp = "未知";

        public String getFullLocation() {
            return formatLocation(country, province, city);
        }
    }

    /**
     * 获取IP地理位置信息
     * 优先使用淘宝IP库，失败则使用备用API
     *
     * @param ip IP地址
     * @return 地理位置信息
     */
    public static LocationInfo getLocation(String ip) {
        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            return getDefaultLocation();
        }

        // 判断是否为内网IP
        if (IpUtils.isInternalIp(ip)) {
            LocationInfo info = new LocationInfo();
            info.setCountry("中国");
            info.setProvince("内网");
            info.setCity("局域网");
            return info;
        }

        // 尝试使用不同的API
        LocationInfo info = getLocationFromIpApi(ip);
        if (info == null) {
            info = getLocationFromIpify(ip);
        }
        if (info == null) {
            info = getDefaultLocation();
        }

        return info;
    }

    /**
     * 使用 ip-api.com 获取位置信息（免费，无需KEY）
     */
    private static LocationInfo getLocationFromIpApi(String ip) {
        try {
            String apiUrl = "http://ip-api.com/json/" + ip + "?lang=zh-CN";
            String response = sendGetRequest(apiUrl);

            if (!StringUtils.hasText(response)) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response);
            String status = root.path("status").asText();

            if ("success".equals(status)) {
                LocationInfo info = new LocationInfo();
                info.setCountry(root.path("country").asText("未知"));
                info.setProvince(root.path("regionName").asText("未知"));
                info.setCity(root.path("city").asText("未知"));
                info.setIsp(root.path("isp").asText("未知"));
                return info;
            }
        } catch (Exception e) {
            log.warn("ip-api.com查询失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 使用 ipapi.co 获取位置信息（备用API）
     */
    private static LocationInfo getLocationFromIpify(String ip) {
        try {
            String apiUrl = "https://ipapi.co/" + ip + "/json/";
            String response = sendGetRequest(apiUrl);

            if (!StringUtils.hasText(response)) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response);

            LocationInfo info = new LocationInfo();
            info.setCountry(root.path("country_name").asText("未知"));
            info.setProvince(root.path("region").asText("未知"));
            info.setCity(root.path("city").asText("未知"));
            info.setIsp(root.path("org").asText("未知"));
            return info;
        } catch (Exception e) {
            log.warn("ipapi.co查询失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 发送GET请求
     */
    private static String sendGetRequest(String urlString) {
        HttpURLConnection conn = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
                );
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                return response.toString();
            }
        } catch (Exception e) {
            log.warn("HTTP请求失败: {}", e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    // ignore
                }
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
        return null;
    }

    /**
     * 获取默认位置信息
     */
    private static LocationInfo getDefaultLocation() {
        LocationInfo info = new LocationInfo();
        info.setCountry("未知");
        info.setProvince("未知");
        info.setCity("未知");
        info.setIsp("未知");
        return info;
    }

    /**
     * 格式化地理位置字符串
     */
    public static String formatLocation(String country, String province, String city) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(country) && !"未知".equals(country)) {
            sb.append(country);
        }
        if (StringUtils.hasText(province) && !"未知".equals(province)) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(province);
        }
        if (StringUtils.hasText(city) && !"未知".equals(city)) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(city);
        }
        return !sb.isEmpty() ? sb.toString() : "未知";
    }
}
