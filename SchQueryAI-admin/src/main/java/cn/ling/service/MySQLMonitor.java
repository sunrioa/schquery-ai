package cn.ling.service;

import java.sql.*;

public class MySQLMonitor {
    private static final String URL = "jdbc:mysql://localhost:3306/schqueryai?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
    private static final String USER = "root";
    private static final String PASSWORD = "${SECRET_VALUE}";

    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {

            // 1. 获取全局状态变量
            try (ResultSet rs = stmt.executeQuery("SHOW GLOBAL STATUS")) {
                while (rs.next()) {
                    String variable = rs.getString("Variable_name");
                    String value = rs.getString("Value");
                    if ("Threads_connected".equals(variable) ||
                            "Uptime".equals(variable) ||
                            "Questions".equals(variable)) {
                        System.out.println(variable + ": " + value);
                    }
                }
            }

            // 2. 获取全局变量（配置）
            try (ResultSet rs = stmt.executeQuery("SHOW VARIABLES LIKE 'max_connections'")) {
                if (rs.next()) {
                    System.out.println("Max Connections: " + rs.getString("Value"));
                }
            }

            // 3. 查看当前进程（类似 SHOW PROCESSLIST）
            try (ResultSet rs = stmt.executeQuery("SELECT * FROM information_schema.PROCESSLIST")) {
                System.out.println("\nActive Connections:");
                while (rs.next()) {
                    System.out.printf("ID=%s, User=%s, Host=%s, DB=%s, Command=%s, Time=%s, Info=%s%n",
                            rs.getString("ID"),
                            rs.getString("USER"),
                            rs.getString("HOST"),
                            rs.getString("DB"),
                            rs.getString("COMMAND"),
                            rs.getString("TIME"),
                            rs.getString("INFO"));
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}