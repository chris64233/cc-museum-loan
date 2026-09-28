package com.chris64233.cc.museumloan.service;

/**
 * 幂等执行结果。newResult 为 false 时表示本次为重放，jsonBody 是首次执行存档的原始响应。
 */
public record IdempotentResult(int httpStatus, String jsonBody, boolean replayed) {
}
