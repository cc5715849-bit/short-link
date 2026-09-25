package com.hou.shortlink.common;

import lombok.Getter;

/**
 * 业务异常：Service 层校验不通过时抛出，
 * 不用在 Controller 里 try-catch，由全局异常处理器统一转成 Result 返回
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
