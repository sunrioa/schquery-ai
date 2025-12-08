package cn.ling.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 系统监控数据VO
 */
@Data
@Builder
public class SystemMonitorVO {
    
    /**
     * CPU信息
     */
    private CpuInfo cpu;
    
    /**
     * 内存信息
     */
    private MemoryInfo memory;
    
    /**
     * 磁盘信息
     */
    private List<DiskInfo> disks;
    
    /**
     * 网络信息
     */
    private List<NetworkInfo> networks;
    
    /**
     * MySQL信息
     */
    private MySQLInfo mysql;
    
    /**
     * Redis信息
     */
    private RedisInfo redis;
    
    /**
     * 系统信息
     */
    private SystemBasicInfo system;
    
    @Data
    @Builder
    public static class CpuInfo {
        private String name;           // CPU名称
        private int cores;             // 核心数
        private double usage;          // 使用率 %
        private String model;          // 型号
    }
    
    @Data
    @Builder
    public static class MemoryInfo {
        private long total;            // 总内存 (bytes)
        private long used;             // 已使用内存 (bytes)
        private long free;             // 空闲内存 (bytes)
        private double usage;          // 使用率 %
    }
    
    @Data
    @Builder
    public static class DiskInfo {
        private String mount;          // 挂载点
        private String name;           // 磁盘名称
        private long total;            // 总空间 (bytes)
        private long used;             // 已使用空间 (bytes)
        private long free;             // 空闲空间 (bytes)
        private double usage;          // 使用率 %
    }
    
    @Data
    @Builder
    public static class NetworkInfo {
        private String name;           // 网卡名称
        private long bytesRecv;        // 接收字节数
        private long bytesSent;        // 发送字节数
        private String ipv4;           // IPv4地址
    }
    
    @Data
    @Builder
    public static class MySQLInfo {
        private boolean connected;     // 是否连接
        private int threadsConnected;  // 当前连接数
        private int maxConnections;    // 最大连接数
        private long uptime;           // 运行时间 (秒)
        private long questions;        // 查询次数
        private String version;        // 版本
    }
    
    @Data
    @Builder
    public static class RedisInfo {
        private boolean connected;     // 是否连接
        private String version;        // 版本
        private int connectedClients;  // 连接客户端数
        private long usedMemory;       // 已使用内存 (bytes)
        private long totalMemory;      // 总内存 (bytes)
        private long uptime;           // 运行时间 (秒)
        private long keyCount;         // Key数量
    }
    
    @Data
    @Builder
    public static class SystemBasicInfo {
        private String osName;         // 操作系统名称
        private String osVersion;      // 操作系统版本
        private String hostName;       // 主机名
        private long uptime;           // 系统运行时间 (秒)
        private String arch;           // 系统架构
        private String javaVersion;    // JDK版本
        private long jvmTotalMemory;   // JVM总内存
    }
}
