package com.chris64233.cc.museumloan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 借展机构。
 */
@Entity
@Table(name = "institution", uniqueConstraints = {
        @UniqueConstraint(name = "uk_institution_code", columnNames = "code")
})
public class Institution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 唯一机构编号 */
    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    /** 可承担的最高风险等级 */
    @Enumerated(EnumType.STRING)
    @Column(name = "max_risk", nullable = false, length = 16)
    private RiskLevel maxRisk;

    protected Institution() {
    }

    public Institution(String code, String name, RiskLevel maxRisk) {
        this.code = code;
        this.name = name;
        this.maxRisk = maxRisk;
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

    public RiskLevel getMaxRisk() {
        return maxRisk;
    }
}
