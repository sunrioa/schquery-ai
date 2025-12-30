package cn.ling.service;

import cn.ling.domain.pojo.SysConfig;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 系统配置（KV）Service
 */
public interface SysConfigService extends IService<SysConfig> {

    /**
     * 按 key 读取配置值，不存在返回 null。
     */
    String getConfigValue(String key);

    /**
     * 新增或更新配置。
     */
    boolean upsertConfig(String key, String value, String name, String remark);
}

