package cn.ling.service;

import redis.clients.jedis.Jedis;

import java.util.LinkedHashMap;
import java.util.Map;

public class text1 {

    public static void main(String[] args) {
        // try-with-resources to close the connection automatically
        try (Jedis jedis = new Jedis("redis://localhost:5580");) {
            jedis.auth("${SECRET_VALUE}");
            // 获取Redis的基本信息
            String info = jedis.info();
            System.out.println(info);

            // 获取所有信息字符串并手动解析成键值对
            String infoAll = jedis.info("all");
            Map<String, String> infoMap = new LinkedHashMap<>();
            for (String line : infoAll.split("\\r?\\n")) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int colon = line.indexOf(':');
                if (colon > 0 && colon < line.length() - 1) {
                    infoMap.put(line.substring(0, colon), line.substring(colon + 1));
                }
            }

            for (Map.Entry<String, String> entry : infoMap.entrySet()) {
                System.out.println(entry.getKey() + ": " + entry.getValue());
            }
        }
    }
}


