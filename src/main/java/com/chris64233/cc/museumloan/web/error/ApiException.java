package com.chris64233.cc.museumloan.web.error;

import java.util.List;

import org.springframework.http.HttpStatus;

/** 业务异常基类：携带 HTTP 状态、错误码与明细。 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String summary;
    private final List<String> details;

    protected ApiException(HttpStatus status, String code, String message, List<String> details) {
        super(details == null || details.isEmpty() ? message
                : message + ": " + String.join("; ", details));
        this.status = status;
        this.code = code;
        this.summary = message;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    /** 不含明细的概述信息，用于错误响应的 message 字段。 */
    public String getSummary() {
        return summary;
    }

    public List<String> getDetails() {
        return details;
    }
}
