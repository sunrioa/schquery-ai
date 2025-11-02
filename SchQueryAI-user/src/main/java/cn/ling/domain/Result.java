package cn.ling.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 通用API响应结果封装类
 * @param <T> 响应数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /**
     * 状态码：200表示成功，非200表示异常
     */
    private int code;

    /**
     * 响应消息：成功或异常信息描述
     */
    private String msg;

    /**
     * 响应数据：成功时返回的数据，异常时可为null
     */
    private T data;

    // 成功响应的静态方法

    /**
     * 成功响应（带数据）
     * @param data 响应数据
     * @param <T> 数据类型
     * @return 成功的Result对象
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }

    /**
     * 成功响应（带数据和自定义消息）
     * @param data 响应数据
     * @param msg 自定义消息
     * @param <T> 数据类型
     * @return 成功的Result对象
     */
    public static <T> Result<T> success(T data, String msg) {
        return new Result<>(200, msg, data);
    }

    /**
     * 成功响应（带自定义消息和数据）
     * @param msg 自定义消息
     * @param data 响应数据
     * @param <T> 数据类型
     * @return 成功的Result对象
     */
    public static <T> Result<T> success(String msg, T data) {
        return new Result<>(200, msg, data);
    }

    /**
     * 成功响应（无数据，默认消息）
     * @param <T> 数据类型
     * @return 成功的Result对象
     */
    public static <T> Result<T> success() {
        return new Result<>(200, "操作成功", null);
    }

    // 错误响应的静态方法

    /**
     * 错误响应（带自定义消息，默认错误码）
     * @param msg 错误消息
     * @param <T> 数据类型
     * @return 错误的Result对象
     */
    public static <T> Result<T> error(String msg) {
        return new Result<>(500, msg, null);
    }

    /**
     * 错误响应（带自定义错误码和消息）
     * @param code 错误码
     * @param msg 错误消息
     * @param <T> 数据类型
     * @return 错误的Result对象
     */
    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    /**
     * 错误响应（带自定义错误码、消息和数据）
     * @param code 错误码
     * @param msg 错误消息
     * @param data 错误相关数据（如验证失败的字段信息）
     * @param <T> 数据类型
     * @return 错误的Result对象
     */
    public static <T> Result<T> error(int code, String msg, T data) {
        return new Result<>(code, msg, data);
    }
}
