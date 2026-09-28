package com.chris64233.cc.museumloan.web.dto;

import com.chris64233.cc.museumloan.domain.RiskLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterInstitutionRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name,
        @NotNull RiskLevel maxRisk) {
}
