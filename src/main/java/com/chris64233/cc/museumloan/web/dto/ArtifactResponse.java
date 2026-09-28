package com.chris64233.cc.museumloan.web.dto;

import java.math.BigDecimal;

/** 馆藏品响应。 */
public record ArtifactResponse(

        String catalogNo,
        String name,
        boolean loanable,
        BigDecimal minTemperature,
        BigDecimal maxTemperature,
        BigDecimal minHumidity,
        BigDecimal maxHumidity,
        String transportRisk) {
}
