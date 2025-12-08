package cn.ling.controller;

import cn.ling.Result;
import cn.ling.domain.vo.SystemMonitorVO;
import cn.ling.service.SystemMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统监控控制器
 */
@Slf4j
@RestController
@RequestMapping("/admin/monitor")
@RequiredArgsConstructor
public class SystemMonitorController {

    private final SystemMonitorService systemMonitorService;

    /**
     * 获取系统监控全部数据
     */
    @GetMapping("/all")
    public Result<SystemMonitorVO> getSystemMonitorData() {
        log.info("获取系统监控数据");
        return systemMonitorService.getSystemMonitorData();
    }

    /**
     * 获取CPU信息
     */
    @GetMapping("/cpu")
    public Result<SystemMonitorVO.CpuInfo> getCpuInfo() {
        return systemMonitorService.getCpuInfo();
    }

    /**
     * 获取内存信息
     */
    @GetMapping("/memory")
    public Result<SystemMonitorVO.MemoryInfo> getMemoryInfo() {
        return systemMonitorService.getMemoryInfo();
    }

    /**
     * 获取MySQL状态
     */
    @GetMapping("/mysql")
    public Result<SystemMonitorVO.MySQLInfo> getMySQLInfo() {
        return systemMonitorService.getMySQLInfo();
    }

    /**
     * 获取Redis状态
     */
    @GetMapping("/redis")
    public Result<SystemMonitorVO.RedisInfo> getRedisInfo() {
        return systemMonitorService.getRedisInfo();
    }
}
