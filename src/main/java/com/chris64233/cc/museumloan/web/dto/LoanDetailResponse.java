package com.chris64233.cc.museumloan.web.dto;

import com.chris64233.cc.museumloan.domain.LoanStatus;
import com.chris64233.cc.museumloan.domain.RiskLevel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record LoanDetailResponse(
        Long id,
        InstitutionResponse institution,
        List<ArtifactResponse> artifacts,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal committedMinTemp,
        BigDecimal committedMaxTemp,
        BigDecimal committedMinHumidity,
        BigDecimal committedMaxHumidity,
        RiskLevel transportRisk,
        LoanStatus status,
        String reason,
        OffsetDateTime createdAt,
        OffsetDateTime decidedAt,
        List<AuditLogResponse> auditLogs) {
}
