package cn.ling.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 自定义业务异常类
 * 继承RuntimeException，提供统一的业务异常处理机制
 * 支持自定义错误码和错误消息，便于系统进行统一的异常管理和错误响应
 * 与GlobalExceptionHandler配合使用，实现异常的统一处理和日志记录
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class CustomException extends RuntimeException {

    /**
     * 错误状态码
     * 用于标识不同类型的业务异常，便于前端进行错误分类处理
     * 常用码值：400-参数错误，401-认证失败，403-权限不足，500-服务器错误等
     */
    private int code;

    /**
     * 错误消息
     * 描述具体的业务异常原因，用于向用户展示错误信息
     */
    private String msg;

    /**
     * 构造函数：仅包含错误消息
     * 使用默认错误码500（服务器内部错误）创建异常
     *
     * @param msg 错误消息，描述具体的异常原因
     */
    public CustomException(String msg) {
        this.msg = msg;
    }

    /**
     * 构造函数：包含错误码和错误消息
     * 使用指定的错误码和错误消息创建异常
     *
     * @param code 错误状态码，用于标识异常类型
     * @param msg 错误消息，描述具体的异常原因
     */
    public CustomException(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }


    /**
     * 静态工厂方法：创建仅包含错误消息的异常
     * 使用默认错误码500（服务器内部错误）
     *
     * @param msg 错误消息，描述具体的异常原因
     * @return CustomException实例
     */
    public static CustomException error(String msg) {
        return new CustomException(msg);
    }

    /**
     * 静态工厂方法：创建包含错误码和错误消息的异常
     *
     * @param code 错误状态码，用于标识异常类型
     * @param msg 错误消息，描述具体的异常原因
     * @return CustomException实例
     */
    public static CustomException error(int code, String msg) {
        return new CustomException(code, msg);
    }

}

