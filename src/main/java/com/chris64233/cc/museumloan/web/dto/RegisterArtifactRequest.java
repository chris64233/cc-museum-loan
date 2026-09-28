package com.chris64233.cc.museumloan.web.dto;

import com.chris64233.cc.museumloan.domain.RiskLevel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record RegisterArtifactRequest(
        @NotBlank @Size(max = 64) String catalogNo,
        @NotBlank @Size(max = 200) String name,
        @NotNull Boolean loanable,
        @NotNull @DecimalMin("-100.00") @DecimalMax("100.00") BigDecimal minTemp,
        @NotNull @DecimalMin("-100.00") @DecimalMax("100.00") BigDecimal maxTemp,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal minHumidity,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal maxHumidity,
        @NotNull RiskLevel transportRisk) {
}
