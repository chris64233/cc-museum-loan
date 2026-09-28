package com.chris64233.cc.museumloan.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 借展机构，包含唯一编号及其可承担的最高风险等级。 */
@Entity
@Table(name = "borrowing_institution", uniqueConstraints = {
        @UniqueConstraint(name = "uk_institution_code", columnNames = "code")
})
public class BorrowingInstitution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 唯一机构编号。 */
    @Column(name = "code", nullable = false, updatable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** 机构可承担的最高风险等级（含该等级）。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "max_risk", nullable = false, length = 16)
    private RiskLevel maxRisk;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BorrowingInstitution() {
    }

    public BorrowingInstitution(String code, String name, RiskLevel maxRisk, Instant createdAt) {
        this.code = code;
        this.name = name;
        this.maxRisk = maxRisk;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public RiskLevel getMaxRisk() {
        return maxRisk;
    }

    public void setMaxRisk(RiskLevel maxRisk) {
        this.maxRisk = maxRisk;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
