package cn.ling.exception;

import lombok.Data;

@Data
public class CustomException extends RuntimeException{

    private int code;

    private String msg;

    public CustomException(String msg) {
        this.msg = msg;
    }

    public CustomException(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public static CustomException error(String msg) {
        return new CustomException(msg);
    }

    public static CustomException error(int code, String msg) {
        return new CustomException(code, msg);
    }
}

