package cn.ling.service.impl;

import cn.ling.domain.pojo.SysConfig;
import cn.ling.mapper.SysConfigMapper;
import cn.ling.service.SysConfigService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 系统配置（KV）Service 实现
 */
@Slf4j
@Service
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfig> implements SysConfigService {

    @Override
    public String getConfigValue(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        SysConfig cfg = lambdaQuery()
                .eq(SysConfig::getConfigKey, key.trim())
                .last("limit 1")
                .one();
        return cfg == null ? null : cfg.getConfigValue();
    }

    @Override
    public boolean upsertConfig(String key, String value, String name, String remark) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("config_key不能为空");
        }
        String normalizedKey = key.trim();
        SysConfig cfg = lambdaQuery()
                .eq(SysConfig::getConfigKey, normalizedKey)
                .last("limit 1")
                .one();

        if (cfg == null) {
            cfg = SysConfig.builder()
                    .configKey(normalizedKey)
                    .configName(StringUtils.hasText(name) ? name.trim() : null)
                    .configValue(value)
                    .remark(StringUtils.hasText(remark) ? remark.trim() : null)
                    .status(1)
                    .build();
            return save(cfg);
        }

        cfg.setConfigName(StringUtils.hasText(name) ? name.trim() : cfg.getConfigName());
        cfg.setConfigValue(value);
        cfg.setRemark(StringUtils.hasText(remark) ? remark.trim() : cfg.getRemark());
        if (cfg.getStatus() == null) {
            cfg.setStatus(1);
        }
        return updateById(cfg);
    }
}

