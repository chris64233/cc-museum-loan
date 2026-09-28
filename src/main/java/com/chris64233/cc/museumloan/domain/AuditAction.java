package com.chris64233.cc.museumloan.domain;

/** 审计动作类型。 */
public enum AuditAction {

    SUBMITTED("提交申请"),
    APPROVED("整组批准"),
    REJECTED("拒绝"),
    CANCELLED("取消并释放藏品"),
    REPLAYED("幂等重放");

    private final String label;

    AuditAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
