package cn.ling.service.impl;

import cn.ling.Result;
import cn.ling.domain.vo.SystemMonitorVO;
import cn.ling.service.SystemMonitorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.NetworkIF;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 系统监控服务实现类
 */
@Slf4j
@Service
public class SystemMonitorServiceImpl implements SystemMonitorService {

    @Value("${spring.datasource.url}")
    private String mysqlUrl;

    @Value("${spring.datasource.username}")
    private String mysqlUsername;

    @Value("${spring.datasource.password}")
    private String mysqlPassword;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    private final SystemInfo systemInfo = new SystemInfo();
    private long[] prevTicks;

    @Override
    public Result<SystemMonitorVO> getSystemMonitorData() {
        try {
            SystemMonitorVO vo = SystemMonitorVO.builder()
                    .cpu(getCpuInfoInternal())
                    .memory(getMemoryInfoInternal())
                    .disks(getDiskInfoInternal())
                    .networks(getNetworkInfoInternal())
                    .mysql(getMySQLInfoInternal())
                    .redis(getRedisInfoInternal())
                    .system(getSystemBasicInfoInternal())
                    .build();
            return Result.success(vo);
        } catch (Exception e) {
            log.error("获取系统监控数据失败", e);
            return Result.error("获取系统监控数据失败：" + e.getMessage());
        }
    }

    @Override
    public Result<SystemMonitorVO.CpuInfo> getCpuInfo() {
        try {
            return Result.success(getCpuInfoInternal());
        } catch (Exception e) {
            log.error("获取CPU信息失败", e);
            return Result.error("获取CPU信息失败：" + e.getMessage());
        }
    }

    @Override
    public Result<SystemMonitorVO.MemoryInfo> getMemoryInfo() {
        try {
            return Result.success(getMemoryInfoInternal());
        } catch (Exception e) {
            log.error("获取内存信息失败", e);
            return Result.error("获取内存信息失败：" + e.getMessage());
        }
    }

    @Override
    public Result<SystemMonitorVO.MySQLInfo> getMySQLInfo() {
        try {
            return Result.success(getMySQLInfoInternal());
        } catch (Exception e) {
            log.error("获取MySQL信息失败", e);
            return Result.error("获取MySQL信息失败：" + e.getMessage());
        }
    }

    @Override
    public Result<SystemMonitorVO.RedisInfo> getRedisInfo() {
        try {
            return Result.success(getRedisInfoInternal());
        } catch (Exception e) {
            log.error("获取Redis信息失败", e);
            return Result.error("获取Redis信息失败：" + e.getMessage());
        }
    }

    /**
     * 获取CPU信息
     */
    private SystemMonitorVO.CpuInfo getCpuInfoInternal() {
        CentralProcessor processor = systemInfo.getHardware().getProcessor();
        
        // 计算CPU使用率
        if (prevTicks == null) {
            prevTicks = processor.getSystemCpuLoadTicks();
        }
        double cpuLoad = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100;
        prevTicks = processor.getSystemCpuLoadTicks();
        
        return SystemMonitorVO.CpuInfo.builder()
                .name(processor.getProcessorIdentifier().getName())
                .cores(processor.getLogicalProcessorCount())
                .usage(Math.round(cpuLoad * 100.0) / 100.0)
                .model(processor.getProcessorIdentifier().getModel())
                .build();
    }

    /**
     * 获取内存信息
     */
    private SystemMonitorVO.MemoryInfo getMemoryInfoInternal() {
        GlobalMemory memory = systemInfo.getHardware().getMemory();
        long total = memory.getTotal();
        long available = memory.getAvailable();
        long used = total - available;
        double usage = (double) used / total * 100;
        
        return SystemMonitorVO.MemoryInfo.builder()
                .total(total)
                .used(used)
                .free(available)
                .usage(Math.round(usage * 100.0) / 100.0)
                .build();
    }

    /**
     * 获取磁盘信息
     */
    private List<SystemMonitorVO.DiskInfo> getDiskInfoInternal() {
        List<SystemMonitorVO.DiskInfo> diskInfoList = new ArrayList<>();
        OperatingSystem os = systemInfo.getOperatingSystem();
        FileSystem fileSystem = os.getFileSystem();
        
        for (OSFileStore fs : fileSystem.getFileStores()) {
            long total = fs.getTotalSpace();
            long free = fs.getUsableSpace();
            long used = total - free;
            double usage = total > 0 ? (double) used / total * 100 : 0;
            
            diskInfoList.add(SystemMonitorVO.DiskInfo.builder()
                    .mount(fs.getMount())
                    .name(fs.getName())
                    .total(total)
                    .used(used)
                    .free(free)
                    .usage(Math.round(usage * 100.0) / 100.0)
                    .build());
        }
        return diskInfoList;
    }

    /**
     * 获取网络信息
     */
    private List<SystemMonitorVO.NetworkInfo> getNetworkInfoInternal() {
        List<SystemMonitorVO.NetworkInfo> networkInfoList = new ArrayList<>();
        List<NetworkIF> networkIFs = systemInfo.getHardware().getNetworkIFs();
        
        for (NetworkIF net : networkIFs) {
            net.updateAttributes();
            String[] ipv4Addrs = net.getIPv4addr();
            String ipv4 = ipv4Addrs.length > 0 ? ipv4Addrs[0] : "";
            
            networkInfoList.add(SystemMonitorVO.NetworkInfo.builder()
                    .name(net.getName())
                    .bytesRecv(net.getBytesRecv())
                    .bytesSent(net.getBytesSent())
                    .ipv4(ipv4)
                    .build());
        }
        return networkInfoList;
    }

    /**
     * 获取MySQL信息
     */
    private SystemMonitorVO.MySQLInfo getMySQLInfoInternal() {
        SystemMonitorVO.MySQLInfo.MySQLInfoBuilder builder = SystemMonitorVO.MySQLInfo.builder();
        
        try (Connection conn = DriverManager.getConnection(mysqlUrl, mysqlUsername, mysqlPassword);
             Statement stmt = conn.createStatement()) {
            
            builder.connected(true);
            
            // 获取版本
            try (ResultSet rs = stmt.executeQuery("SELECT VERSION()")) {
                if (rs.next()) {
                    builder.version(rs.getString(1));
                }
            }
            
            // 获取状态变量
            try (ResultSet rs = stmt.executeQuery("SHOW GLOBAL STATUS")) {
                while (rs.next()) {
                    String variable = rs.getString("Variable_name");
                    String value = rs.getString("Value");
                    switch (variable) {
                        case "Threads_connected":
                            builder.threadsConnected(Integer.parseInt(value));
                            break;
                        case "Uptime":
                            builder.uptime(Long.parseLong(value));
                            break;
                        case "Questions":
                            builder.questions(Long.parseLong(value));
                            break;
                    }
                }
            }
            
            // 获取最大连接数
            try (ResultSet rs = stmt.executeQuery("SHOW VARIABLES LIKE 'max_connections'")) {
                if (rs.next()) {
                    builder.maxConnections(Integer.parseInt(rs.getString("Value")));
                }
            }
            
        } catch (SQLException e) {
            log.error("获取MySQL信息失败", e);
            builder.connected(false);
        }
        
        return builder.build();
    }

    /**
     * 获取Redis信息
     */
    private SystemMonitorVO.RedisInfo getRedisInfoInternal() {
        SystemMonitorVO.RedisInfo.RedisInfoBuilder builder = SystemMonitorVO.RedisInfo.builder();
        
        try {
            RedisConnection connection = redisConnectionFactory.getConnection();
            
            builder.connected(true);
            
            // 获取Redis INFO信息
            java.util.Properties info = connection.serverCommands().info();
            if (info != null) {
                String version = info.getProperty("redis_version");
                if (version != null) {
                    builder.version(version);
                }
                
                String connectedClients = info.getProperty("connected_clients");
                if (connectedClients != null) {
                    builder.connectedClients(Integer.parseInt(connectedClients));
                }
                
                String usedMemory = info.getProperty("used_memory");
                if (usedMemory != null) {
                    builder.usedMemory(Long.parseLong(usedMemory));
                }
                
                String totalMemory = info.getProperty("total_system_memory");
                if (totalMemory != null) {
                    builder.totalMemory(Long.parseLong(totalMemory));
                }
                
                String uptime = info.getProperty("uptime_in_seconds");
                if (uptime != null) {
                    builder.uptime(Long.parseLong(uptime));
                }
            }
            
            // 获取Key数量
            Long dbSize = connection.serverCommands().dbSize();
            builder.keyCount(dbSize != null ? dbSize : 0L);
            
            connection.close();
            
        } catch (Exception e) {
            log.error("获取Redis信息失败", e);
            builder.connected(false);
        }
        
        return builder.build();
    }

    /**
     * 获取系统基本信息
     */
    private SystemMonitorVO.SystemBasicInfo getSystemBasicInfoInternal() {
        OperatingSystem os = systemInfo.getOperatingSystem();
        Runtime runtime = Runtime.getRuntime();
        
        return SystemMonitorVO.SystemBasicInfo.builder()
                .osName(os.getFamily())
                .osVersion(os.getVersionInfo().getVersion())
                .hostName(os.getNetworkParams().getHostName())
                .uptime(os.getSystemUptime())
                .arch(os.getBitness() + "-bit")
                .javaVersion(System.getProperty("java.version"))
                .jvmTotalMemory(runtime.totalMemory())
                .build();
    }
}
