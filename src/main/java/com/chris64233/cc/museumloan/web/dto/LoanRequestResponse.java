package com.chris64233.cc.museumloan.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 借展申请详情响应。 */
public record LoanRequestResponse(

        String requestNo,
        String status,
        String institutionCode,
        String institutionName,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal promisedMinTemperature,
        BigDecimal promisedMaxTemperature,
        BigDecimal promisedMinHumidity,
        BigDecimal promisedMaxHumidity,
        String transportPlanRisk,
        String reason,
        Instant submittedAt,
        Instant decidedAt,
        List<Item> items,
        List<Audit> audits) {

    public record Item(String catalogNo, String artifactName, String transportRisk) {
    }

    public record Audit(String action, String fromStatus, String toStatus,
                        String reason, Instant occurredAt) {
    }
}
