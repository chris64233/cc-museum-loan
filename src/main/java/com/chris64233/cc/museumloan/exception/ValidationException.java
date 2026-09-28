package com.chris64233.cc.museumloan.exception;

/** 请求内容校验失败（400）。 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
