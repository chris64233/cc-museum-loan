package com.chris64233.cc.museumloan.web.dto;

import com.chris64233.cc.museumloan.domain.RiskLevel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 借展申请。藏品号列表不允许为空，重复与跨字段一致性由服务层完整校验。
 */
public record ApplyLoanRequest(
        @NotBlank String institutionCode,
        @NotEmpty List<@NotBlank String> artifactCatalogNos,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @DecimalMin("-100.00") @DecimalMax("100.00") BigDecimal committedMinTemp,
        @NotNull @DecimalMin("-100.00") @DecimalMax("100.00") BigDecimal committedMaxTemp,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal committedMinHumidity,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal committedMaxHumidity,
        @NotNull RiskLevel transportRisk) {
}
