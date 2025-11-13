package cn.ling;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 通用API响应结果封装类
 * 提供统一的API响应格式，包含状态码、消息和数据三个核心字段
 * 支持成功和错误响应的快速构建，便于前后端接口的统一处理
 * 包含多种静态工厂方法，简化响应对象的创建过程
 *
 * @param <T> 响应数据类型，支持泛型以适应不同业务场景
 */
@Slf4j
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
     * 成功响应（带数据，默认消息）
     * 创建包含数据和默认成功消息的响应对象，用于成功的业务操作
     *
     * @param data 响应数据，可以是业务实体、列表、Map等任意类型
     * @param <T> 数据类型，通过泛型确保类型安全
     * @return 成功的Result对象，状态码为200，消息为"操作成功"
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }

    /**
     * 成功响应（带数据和自定义消息）
     * 创建包含数据和自定义成功消息的响应对象，用于需要特殊提示的成功操作
     *
     * @param data 响应数据，可以是业务实体、列表、Map等任意类型
     * @param msg 自定义成功消息，用于更详细的操作结果描述
     * @param <T> 数据类型，通过泛型确保类型安全
     * @return 成功的Result对象，状态码为200
     */
    public static <T> Result<T> success(T data, String msg) {
        return new Result<>(200, msg, data);
    }

    /**
     * 成功响应（带自定义消息和数据）
     * 创建包含数据和自定义成功消息的响应对象，参数顺序与上方方法不同
     *
     * @param msg 自定义成功消息，用于更详细的操作结果描述
     * @param data 响应数据，可以是业务实体、列表、Map等任意类型
     * @param <T> 数据类型，通过泛型确保类型安全
     * @return 成功的Result对象，状态码为200
     */
    public static <T> Result<T> success(String msg, T data) {
        return new Result<>(200, msg, data);
    }

    /**
     * 成功响应（无数据，默认消息）
     * 创建仅包含成功消息的响应对象，用于不需要返回数据的成功操作
     * 如删除操作、更新操作等只需要确认操作成功的场景
     *
     * @param <T> 数据类型，通过泛型确保类型安全
     * @return 成功的Result对象，状态码为200，数据为null
     */
    public static <T> Result<T> success() {
        return new Result<>(200, "操作成功", null);
    }

    // 错误响应的静态方法

    /**
     * 错误响应（带自定义消息，默认错误码500）
     * 创建包含错误消息的响应对象，使用默认的服务器内部错误状态码
     * 适用于一般性的业务异常和系统错误
     *
     * @param msg 错误消息，描述具体的错误原因或提示信息
     * @param <T> 数据类型，通过泛型确保类型安全
     * @return 错误的Result对象，状态码为500，数据为null
     */
    public static <T> Result<T> error(String msg) {
        return new Result<>(500, msg, null);
    }

    /**
     * 错误响应（带自定义错误码和消息）
     * 创建包含自定义错误码和错误消息的响应对象，用于不同类型的业务异常
     * 支持根据业务需要定义不同的错误码，便于前端进行错误分类处理
     *
     * @param code 错误码，如400表示参数错误，401表示认证失败等
     * @param msg 错误消息，描述具体的错误原因或提示信息
     * @param <T> 数据类型，通过泛型确保类型安全
     * @return 错误的Result对象，数据为null
     */
    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    /**
     * 错误响应（带自定义错误码、消息和数据）
     * 创建包含完整错误信息的响应对象，支持携带错误相关的详细数据
     * 适用于需要向客户端返回错误详细信息的场景，如字段验证失败信息
     *
     * @param code 错误码，如400表示参数错误，401表示认证失败等
     * @param msg 错误消息，描述具体的错误原因或提示信息
     * @param data 错误相关数据，可以是验证失败的字段信息、错误详情等
     * @param <T> 数据类型，通过泛型确保类型安全
     * @return 错误的Result对象，包含完整的错误信息
     */
    public static <T> Result<T> error(int code, String msg, T data) {
        return new Result<>(code, msg, data);
    }
}
