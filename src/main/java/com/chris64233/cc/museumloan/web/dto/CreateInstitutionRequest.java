package com.chris64233.cc.museumloan.web.dto;

import com.chris64233.cc.museumloan.domain.RiskLevel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 登记借展机构请求。 */
public record CreateInstitutionRequest(

        @NotBlank(message = "机构编号不能为空")
        @Size(max = 64)
        String code,

        @NotBlank(message = "机构名称不能为空")
        @Size(max = 200)
        String name,

        @NotNull(message = "可承担最高风险等级不能为空")
        RiskLevel maxRisk) {
}
