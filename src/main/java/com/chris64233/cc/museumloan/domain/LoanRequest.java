package com.chris64233.cc.museumloan.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 借展申请：一次申请多件馆藏品，整组原子批准。
 */
@Entity
@Table(name = "loan_request", uniqueConstraints = {
        @UniqueConstraint(name = "uk_loan_request_no", columnNames = "request_no")
})
public class LoanRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 业务申请编号。 */
    @Column(name = "request_no", nullable = false, updatable = false, length = 64)
    private String requestNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false, updatable = false)
    private BorrowingInstitution institution;

    /** 借展起始日期（闭区间，含当天）。 */
    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    /** 借展结束日期（闭区间，含当天）。 */
    @Column(name = "end_date", nullable = false, updatable = false)
    private LocalDate endDate;

    /** 承诺温度下限（摄氏度，含）。 */
    @Column(name = "promised_min_temperature", nullable = false, updatable = false, precision = 6, scale = 2)
    private BigDecimal promisedMinTemperature;

    /** 承诺温度上限（摄氏度，含）。 */
    @Column(name = "promised_max_temperature", nullable = false, updatable = false, precision = 6, scale = 2)
    private BigDecimal promisedMaxTemperature;

    /** 承诺相对湿度下限（百分比，含）。 */
    @Column(name = "promised_min_humidity", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal promisedMinHumidity;

    /** 承诺相对湿度上限（百分比，含）。 */
    @Column(name = "promised_max_humidity", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal promisedMaxHumidity;

    /** 运输方案风险等级。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "transport_plan_risk", nullable = false, updatable = false, length = 16)
    private RiskLevel transportPlanRisk;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private LoanStatus status;

    /** 拒绝或取消原因（批准通过时为空）。 */
    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @OneToMany(mappedBy = "loanRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LoanRequestItem> items = new ArrayList<>();

    protected LoanRequest() {
    }

    public LoanRequest(String requestNo, BorrowingInstitution institution,
                       LocalDate startDate, LocalDate endDate,
                       BigDecimal promisedMinTemperature, BigDecimal promisedMaxTemperature,
                       BigDecimal promisedMinHumidity, BigDecimal promisedMaxHumidity,
                       RiskLevel transportPlanRisk, Instant submittedAt) {
        this.requestNo = requestNo;
        this.institution = institution;
        this.startDate = startDate;
        this.endDate = endDate;
        this.promisedMinTemperature = promisedMinTemperature;
        this.promisedMaxTemperature = promisedMaxTemperature;
        this.promisedMinHumidity = promisedMinHumidity;
        this.promisedMaxHumidity = promisedMaxHumidity;
        this.transportPlanRisk = transportPlanRisk;
        this.status = LoanStatus.PENDING;
        this.submittedAt = submittedAt;
    }

    public void addItem(LoanRequestItem item) {
        items.add(item);
    }

    public List<LoanRequestItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void approve(Instant decidedAt) {
        this.status = LoanStatus.APPROVED;
        this.decidedAt = decidedAt;
        this.reason = null;
    }

    public void reject(String reason, Instant decidedAt) {
        this.status = LoanStatus.REJECTED;
        this.reason = reason;
        this.decidedAt = decidedAt;
    }

    public void cancel(String reason, Instant decidedAt) {
        this.status = LoanStatus.CANCELLED;
        this.reason = reason;
        this.decidedAt = decidedAt;
    }

    public Long getId() {
        return id;
    }

    public String getRequestNo() {
        return requestNo;
    }

    public BorrowingInstitution getInstitution() {
        return institution;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public BigDecimal getPromisedMinTemperature() {
        return promisedMinTemperature;
    }

    public BigDecimal getPromisedMaxTemperature() {
        return promisedMaxTemperature;
    }

    public BigDecimal getPromisedMinHumidity() {
        return promisedMinHumidity;
    }

    public BigDecimal getPromisedMaxHumidity() {
        return promisedMaxHumidity;
    }

    public RiskLevel getTransportPlanRisk() {
        return transportPlanRisk;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
