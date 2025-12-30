package cn.ling.domain.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 系统参数配置表（KV）：sys_config
 */
@TableName(value = "sys_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysConfig {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String configKey;
    private String configName;
    private String configValue;
    private String remark;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

