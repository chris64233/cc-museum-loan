package com.chris64233.cc.museumloan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 申请状态操作审计记录（申请、批准、拒绝、取消均留痕）。
 */
@Entity
@Table(name = "loan_audit_log")
public class LoanAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loan_request_id", nullable = false)
    private Long loanRequestId;

    /** SUBMITTED / APPROVED / REJECTED / CANCELLED */
    @Column(nullable = false, length = 32)
    private String action;

    @Column(length = 1000)
    private String reason;

    @Column(length = 100)
    private String operator;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected LoanAuditLog() {
    }

    public LoanAuditLog(Long loanRequestId, String action, String reason,
                        String operator, OffsetDateTime createdAt) {
        this.loanRequestId = loanRequestId;
        this.action = action;
        this.reason = reason;
        this.operator = operator;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getLoanRequestId() {
        return loanRequestId;
    }

    public String getAction() {
        return action;
    }

    public String getReason() {
        return reason;
    }

    public String getOperator() {
        return operator;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
