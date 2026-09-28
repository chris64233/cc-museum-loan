package com.chris64233.cc.museumloan.web.dto;

import com.chris64233.cc.museumloan.domain.Artifact;
import com.chris64233.cc.museumloan.domain.RiskLevel;
import java.math.BigDecimal;

public record ArtifactResponse(
        Long id,
        String catalogNo,
        String name,
        boolean loanable,
        BigDecimal minTemp,
        BigDecimal maxTemp,
        BigDecimal minHumidity,
        BigDecimal maxHumidity,
        RiskLevel transportRisk) {

    public static ArtifactResponse from(Artifact a) {
        return new ArtifactResponse(a.getId(), a.getCatalogNo(), a.getName(), a.isLoanable(),
                a.getMinTemp(), a.getMaxTemp(), a.getMinHumidity(), a.getMaxHumidity(),
                a.getTransportRisk());
    }
}
