package cn.ling.domain.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 操作日志表
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "operation_logs")
public class OperationLog {
    /**
     * 日志ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 操作人（用户名或系统）
     */
    private String operator;

    /**
     * 操作类型（如：添加敏感词、删除用户等）
     */
    private String action;

    /**
     * 操作详情
     */
    private String detail;

    /**
     * 操作状态（1-成功，0-失败）
     */
    private Integer status;

    /**
     * 操作IP地址
     */
    private String ipAddress;

    /**
     * 用户设备信息
     */
    private String userAgent;

    /**
     * 操作时间
     */
    private Date createTime;
}
