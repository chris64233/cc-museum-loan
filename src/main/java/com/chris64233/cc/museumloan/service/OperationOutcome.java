package com.chris64233.cc.museumloan.service;

/**
 * 幂等业务动作的执行产物。
 *
 * @param loanRequestId 关联的借展申请 id（无关联时为 null）
 * @param httpStatus    首次执行的 HTTP 状态码
 * @param response      返回给客户端的响应对象（由执行器序列化存档）
 */
public record OperationOutcome(Long loanRequestId, int httpStatus, Object response) {
}
