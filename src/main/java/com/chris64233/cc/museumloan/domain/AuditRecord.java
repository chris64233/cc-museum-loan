package com.chris64233.cc.museumloan.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 借展申请审计记录：提交、批准、拒绝、取消等状态操作均追加一条不可变记录。
 */
@Entity
@Table(name = "audit_record", indexes = {
        @jakarta.persistence.Index(name = "idx_audit_request", columnList = "loan_request_id,occurred_at")
})
public class AuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_request_id", nullable = false, updatable = false)
    private LoanRequest loanRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false, length = 16)
    private AuditAction action;

    /** 操作前状态（提交动作为空）。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false, length = 16)
    private LoanStatus fromStatus;

    /** 操作后状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false, length = 16)
    private LoanStatus toStatus;

    /** 拒绝/取消原因，或失败时的说明。 */
    @Column(name = "reason", updatable = false, length = 1000)
    private String reason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditRecord() {
    }

    public AuditRecord(LoanRequest loanRequest, AuditAction action,
                       LoanStatus fromStatus, LoanStatus toStatus,
                       String reason, Instant occurredAt) {
        this.loanRequest = loanRequest;
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public LoanRequest getLoanRequest() {
        return loanRequest;
    }

    public AuditAction getAction() {
        return action;
    }

    public LoanStatus getFromStatus() {
        return fromStatus;
    }

    public LoanStatus getToStatus() {
        return toStatus;
    }

    public String getReason() {
        return reason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
