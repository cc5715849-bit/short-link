package com.hou.shortlink.common;

import lombok.Getter;

/**
 * 统一返回结构，所有接口都返回这个格式：
 * {"code":0,"message":"ok","data":{}}
 * code=0 表示成功，其他为业务错误码
 */
@Getter
public class Result<T> {

    private final int code;
    private final String message;
    private final T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功（带数据） */
    public static <T> Result<T> success(T data) {
        return new Result<>(0, "ok", data);
    }

    /** 成功（无数据，比如注册、删除类接口） */
    public static <T> Result<T> success() {
        return success(null);
    }

    /** 失败（指定错误码和提示） */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    /** 失败（直接用错误码枚举） */
    public static <T> Result<T> error(ErrorCode errorCode) {
        return error(errorCode.getCode(), errorCode.getMessage());
    }
}
