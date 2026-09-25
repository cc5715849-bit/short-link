package com.hou.shortlink.common;

/**
 * 业务错误码约定：
 * 0     成功
 * 1xxx  通用/参数类
 * 2xxx  用户/认证类
 * 3xxx  短链类
 */
public enum ErrorCode {

    PARAM_ERROR(1001, "参数错误"),

    USERNAME_EXISTS(2001, "用户名已存在"),
    USERNAME_OR_PASSWORD_WRONG(2002, "用户名或密码错误"),
    UNAUTHORIZED(2401, "未登录或登录已过期"),

    LINK_NOT_FOUND(3001, "短链不存在或无权访问"),
    STATUS_INVALID(3002, "status 只能为 0（禁用）或 1（启用）"),

    SYSTEM_ERROR(5000, "系统繁忙，请稍后再试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
