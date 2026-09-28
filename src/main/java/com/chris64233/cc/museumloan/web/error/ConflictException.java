package com.chris64233.cc.museumloan.web.error;

import java.util.List;

import org.springframework.http.HttpStatus;

/** 业务冲突（409）：日期重叠、幂等键内容冲突、非法状态流转等。 */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        this(message, List.of());
    }

    public ConflictException(String message, List<String> details) {
        super(HttpStatus.CONFLICT, "CONFLICT", message, details);
    }
}
