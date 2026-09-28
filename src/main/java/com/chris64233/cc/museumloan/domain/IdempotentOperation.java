package com.chris64233.cc.museumloan.domain;

/** 幂等键所保护的状态操作。 */
public enum IdempotentOperation {

    SUBMIT("提交借展申请"),
    APPROVE("批准借展申请"),
    REJECT("拒绝借展申请"),
    CANCEL("取消借展申请");

    private final String label;

    IdempotentOperation(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
