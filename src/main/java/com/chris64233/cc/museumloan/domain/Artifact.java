package com.chris64233.cc.museumloan.domain;

import java.math.BigDecimal;
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

/**
 * 馆藏品（文物）。
 *
 * <p>包含唯一藏品号、当前是否可外借、允许的温湿度范围以及自身运输风险等级。
 */
@Entity
@Table(name = "artifact", uniqueConstraints = {
        @UniqueConstraint(name = "uk_artifact_catalog_no", columnNames = "catalog_no")
})
public class Artifact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 唯一藏品号。 */
    @Column(name = "catalog_no", nullable = false, updatable = false, length = 64)
    private String catalogNo;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** 当前是否可外借。 */
    @Column(name = "loanable", nullable = false)
    private boolean loanable;

    /** 允许温度下限（摄氏度，含）。 */
    @Column(name = "min_temperature", nullable = false, precision = 6, scale = 2)
    private BigDecimal minTemperature;

    /** 允许温度上限（摄氏度，含）。 */
    @Column(name = "max_temperature", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxTemperature;

    /** 允许相对湿度下限（百分比，含）。 */
    @Column(name = "min_humidity", nullable = false, precision = 5, scale = 2)
    private BigDecimal minHumidity;

    /** 允许相对湿度上限（百分比，含）。 */
    @Column(name = "max_humidity", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxHumidity;

    /** 藏品自身运输风险等级。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "transport_risk", nullable = false, length = 16)
    private RiskLevel transportRisk;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Artifact() {
    }

    public Artifact(String catalogNo, String name, boolean loanable,
                    BigDecimal minTemperature, BigDecimal maxTemperature,
                    BigDecimal minHumidity, BigDecimal maxHumidity,
                    RiskLevel transportRisk, Instant createdAt) {
        this.catalogNo = catalogNo;
        this.name = name;
        this.loanable = loanable;
        this.minTemperature = minTemperature;
        this.maxTemperature = maxTemperature;
        this.minHumidity = minHumidity;
        this.maxHumidity = maxHumidity;
        this.transportRisk = transportRisk;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getCatalogNo() {
        return catalogNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isLoanable() {
        return loanable;
    }

    public void setLoanable(boolean loanable) {
        this.loanable = loanable;
    }

    public BigDecimal getMinTemperature() {
        return minTemperature;
    }

    public void setMinTemperature(BigDecimal minTemperature) {
        this.minTemperature = minTemperature;
    }

    public BigDecimal getMaxTemperature() {
        return maxTemperature;
    }

    public void setMaxTemperature(BigDecimal maxTemperature) {
        this.maxTemperature = maxTemperature;
    }

    public BigDecimal getMinHumidity() {
        return minHumidity;
    }

    public void setMinHumidity(BigDecimal minHumidity) {
        this.minHumidity = minHumidity;
    }

    public BigDecimal getMaxHumidity() {
        return maxHumidity;
    }

    public void setMaxHumidity(BigDecimal maxHumidity) {
        this.maxHumidity = maxHumidity;
    }

    public RiskLevel getTransportRisk() {
        return transportRisk;
    }

    public void setTransportRisk(RiskLevel transportRisk) {
        this.transportRisk = transportRisk;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
