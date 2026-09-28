package com.chris64233.cc.museumloan.web.dto;

import com.chris64233.cc.museumloan.domain.Institution;
import com.chris64233.cc.museumloan.domain.RiskLevel;

public record InstitutionResponse(Long id, String code, String name, RiskLevel maxRisk) {

    public static InstitutionResponse from(Institution i) {
        return new InstitutionResponse(i.getId(), i.getCode(), i.getName(), i.getMaxRisk());
    }
}
