package com.chris64233.cc.museumloan.web.dto;

import java.time.Instant;
import java.util.List;

/** 统一错误响应体。 */
public record ErrorResponse(

        int status,
        String error,
        String code,
        String message,
        List<String> details,
        Instant timestamp) {
}
