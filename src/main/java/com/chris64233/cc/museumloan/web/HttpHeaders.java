package com.chris64233.cc.museumloan.web;

public final class HttpHeaders {

    /** 客户端请求幂等键，适用于申请、批准、拒绝、取消四类状态操作。 */
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private HttpHeaders() {
    }
}
