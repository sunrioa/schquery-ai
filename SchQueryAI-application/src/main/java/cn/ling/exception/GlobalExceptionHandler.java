package cn.ling.exception;


import cn.ling.domain.Result;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 全局异常处理器：捕获所有异常并统一返回
 */
@ControllerAdvice // 全局异常处理标识
public class GlobalExceptionHandler {

    /**
     * 捕获所有异常（Exception是所有异常的父类）
     * @ExceptionHandler(Exception.class)：指定处理所有Exception类型的异常
     * @ResponseBody：将返回结果转为JSON格式
     */
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public Result<String> handleAllException(Exception exception) {

        if (exception instanceof CustomException customException){
            return Result.error(customException.getCode(), customException.getMsg());
        }

        // 获取异常消息（如果异常消息为null，给一个默认提示）
        // 返回统一错误结果
        return Result.error(exception.getMessage() != null ? exception.getMessage() : "系统发生未知异常");
    }
}
