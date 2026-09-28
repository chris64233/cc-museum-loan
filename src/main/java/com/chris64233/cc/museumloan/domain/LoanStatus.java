package com.chris64233.cc.museumloan.domain;

public enum LoanStatus {
    /** 待审批 */
    PENDING,
    /** 已批准（借展尚未开始） */
    APPROVED,
    /** 已拒绝 */
    REJECTED,
    /** 已取消（仅批准后、借展开始前可取消） */
    CANCELLED
}
