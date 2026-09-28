package com.chris64233.cc.museumloan.exception;

/**
 * 业务规则冲突（409）：同一幂等键内容不一致，或借展审批/拒绝/取消规则不满足。
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
