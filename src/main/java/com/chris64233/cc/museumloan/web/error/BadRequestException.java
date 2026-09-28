package com.chris64233.cc.museumloan.web.error;

import java.util.List;

import org.springframework.http.HttpStatus;

/** 请求内容不合法（400）。 */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        this(message, List.of());
    }

    public BadRequestException(String message, List<String> details) {
        super(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message, details);
    }
}
