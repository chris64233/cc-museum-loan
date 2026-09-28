package com.chris64233.cc.museumloan.domain;

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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次借展申请（整组藏品原子批准/拒绝/取消）。
 */
@Entity
@Table(name = "loan_request", indexes = {
        @jakarta.persistence.Index(name = "idx_loan_request_status", columnList = "status")
})
public class LoanRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "committed_min_temp", nullable = false, precision = 6, scale = 2)
    private BigDecimal committedMinTemp;

    @Column(name = "committed_max_temp", nullable = false, precision = 6, scale = 2)
    private BigDecimal committedMaxTemp;

    @Column(name = "committed_min_humidity", nullable = false, precision = 5, scale = 2)
    private BigDecimal committedMinHumidity;

    @Column(name = "committed_max_humidity", nullable = false, precision = 5, scale = 2)
    private BigDecimal committedMaxHumidity;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_risk", nullable = false, length = 16)
    private RiskLevel transportRisk;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LoanStatus status = LoanStatus.PENDING;

    @Column(length = 1000)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @OneToMany(mappedBy = "loanRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<LoanRequestItem> items = new ArrayList<>();

    protected LoanRequest() {
    }

    public LoanRequest(Institution institution, LocalDate startDate, LocalDate endDate,
                       BigDecimal committedMinTemp, BigDecimal committedMaxTemp,
                       BigDecimal committedMinHumidity, BigDecimal committedMaxHumidity,
                       RiskLevel transportRisk, OffsetDateTime createdAt) {
        this.institution = institution;
        this.startDate = startDate;
        this.endDate = endDate;
        this.committedMinTemp = committedMinTemp;
        this.committedMaxTemp = committedMaxTemp;
        this.committedMinHumidity = committedMinHumidity;
        this.committedMaxHumidity = committedMaxHumidity;
        this.transportRisk = transportRisk;
        this.status = LoanStatus.PENDING;
        this.createdAt = createdAt;
    }

    public void addItem(Artifact artifact) {
        items.add(new LoanRequestItem(this, artifact));
    }

    public void approve(OffsetDateTime at) {
        this.status = LoanStatus.APPROVED;
        this.decidedAt = at;
    }

    public void reject(String reason, OffsetDateTime at) {
        this.status = LoanStatus.REJECTED;
        this.reason = reason;
        this.decidedAt = at;
    }

    public void cancel(String reason, OffsetDateTime at) {
        this.status = LoanStatus.CANCELLED;
        this.reason = reason;
        this.decidedAt = at;
    }

    public Long getId() {
        return id;
    }

    public Institution getInstitution() {
        return institution;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public BigDecimal getCommittedMinTemp() {
        return committedMinTemp;
    }

    public BigDecimal getCommittedMaxTemp() {
        return committedMaxTemp;
    }

    public BigDecimal getCommittedMinHumidity() {
        return committedMinHumidity;
    }

    public BigDecimal getCommittedMaxHumidity() {
        return committedMaxHumidity;
    }

    public RiskLevel getTransportRisk() {
        return transportRisk;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }

    public List<LoanRequestItem> getItems() {
        return items;
    }
}
