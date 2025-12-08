package cn.ling.service;

import cn.ling.Result;
import cn.ling.domain.vo.SystemMonitorVO;

/**
 * 系统监控服务接口
 */
public interface SystemMonitorService {
    
    /**
     * 获取系统监控数据
     * @return 系统监控数据
     */
    Result<SystemMonitorVO> getSystemMonitorData();
    
    /**
     * 获取CPU使用率
     * @return CPU信息
     */
    Result<SystemMonitorVO.CpuInfo> getCpuInfo();
    
    /**
     * 获取内存信息
     * @return 内存信息
     */
    Result<SystemMonitorVO.MemoryInfo> getMemoryInfo();
    
    /**
     * 获取MySQL状态
     * @return MySQL信息
     */
    Result<SystemMonitorVO.MySQLInfo> getMySQLInfo();
    
    /**
     * 获取Redis状态
     * @return Redis信息
     */
    Result<SystemMonitorVO.RedisInfo> getRedisInfo();
}
