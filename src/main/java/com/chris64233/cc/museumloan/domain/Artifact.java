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
import java.math.BigDecimal;

/**
 * 馆藏品。
 */
@Entity
@Table(name = "artifact", uniqueConstraints = {
        @UniqueConstraint(name = "uk_artifact_catalog_no", columnNames = "catalog_no")
})
public class Artifact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 唯一藏品号 */
    @Column(name = "catalog_no", nullable = false, length = 64)
    private String catalogNo;

    @Column(nullable = false, length = 200)
    private String name;

    /** 是否可外借 */
    @Column(name = "loanable", nullable = false)
    private boolean loanable = true;

    @Column(name = "min_temp", nullable = false, precision = 6, scale = 2)
    private BigDecimal minTemp;

    @Column(name = "max_temp", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxTemp;

    @Column(name = "min_humidity", nullable = false, precision = 5, scale = 2)
    private BigDecimal minHumidity;

    @Column(name = "max_humidity", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxHumidity;

    /** 运输风险等级 */
    @Enumerated(EnumType.STRING)
    @Column(name = "transport_risk", nullable = false, length = 16)
    private RiskLevel transportRisk;

    protected Artifact() {
    }

    public Artifact(String catalogNo, String name, boolean loanable,
                    BigDecimal minTemp, BigDecimal maxTemp,
                    BigDecimal minHumidity, BigDecimal maxHumidity,
                    RiskLevel transportRisk) {
        this.catalogNo = catalogNo;
        this.name = name;
        this.loanable = loanable;
        this.minTemp = minTemp;
        this.maxTemp = maxTemp;
        this.minHumidity = minHumidity;
        this.maxHumidity = maxHumidity;
        this.transportRisk = transportRisk;
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

    public boolean isLoanable() {
        return loanable;
    }

    public void setLoanable(boolean loanable) {
        this.loanable = loanable;
    }

    public BigDecimal getMinTemp() {
        return minTemp;
    }

    public BigDecimal getMaxTemp() {
        return maxTemp;
    }

    public BigDecimal getMinHumidity() {
        return minHumidity;
    }

    public BigDecimal getMaxHumidity() {
        return maxHumidity;
    }

    public RiskLevel getTransportRisk() {
        return transportRisk;
    }
}
