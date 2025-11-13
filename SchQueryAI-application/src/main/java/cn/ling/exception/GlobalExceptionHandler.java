package cn.ling.exception;

import cn.ling.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 全局异常处理器
 * 统一捕获和处理应用程序中的所有异常，返回标准化的错误响应格式
 * 避免异常信息直接暴露给前端，提升系统安全性和用户体验
 */
@Slf4j
@ControllerAdvice // 全局异常处理标识，声明为全局异常处理器
public class GlobalExceptionHandler {

    /**
     * 捕获所有异常的统一处理方法
     * Exception是所有异常的父类，此方法会捕获所有未被其他异常处理方法处理的异常
     *
     * @ExceptionHandler(Exception.class)：指定处理所有Exception类型的异常
     * @ResponseBody：将返回结果转为JSON格式，确保前端能正确解析
     *
     * @param exception 捕获到的异常对象
     * @return 统一格式的错误响应结果
     */
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public Result<String> handleAllException(Exception exception) {
        // 记录异常详细信息，便于问题排查
        log.error("全局异常处理器捕获到异常: {}", exception.getClass().getSimpleName(), exception);

        try {
            // 检查是否为自定义业务异常
            if (exception instanceof CustomException customException) {
                int code = customException.getCode();
                String message = customException.getMsg();

                log.info("处理自定义业务异常 - 错误码: {}, 错误信息: {}", code, message);
                return Result.error(code, message);
            }

            // 处理系统异常（非业务异常）
            String errorMessage = exception.getMessage();

            // 如果异常消息为空，使用默认提示
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                errorMessage = "系统发生未知异常";
            }

            log.warn("处理系统异常: {}", errorMessage);
            return Result.error(errorMessage);

        } catch (Exception handlerException) {
            // 异常处理器本身发生异常的情况
            log.error("异常处理器发生内部错误: {}", handlerException.getMessage(), handlerException);
            return Result.error("系统异常处理失败");
        }
    }
}
