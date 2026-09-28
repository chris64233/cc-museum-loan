package com.chris64233.cc.museumloan.web.dto;

import java.math.BigDecimal;

import com.chris64233.cc.museumloan.domain.RiskLevel;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 登记馆藏品请求。 */
public record CreateArtifactRequest(

        @NotBlank(message = "藏品号不能为空")
        @Size(max = 64)
        String catalogNo,

        @NotBlank(message = "藏品名称不能为空")
        @Size(max = 200)
        String name,

        boolean loanable,

        @NotNull(message = "允许最低温度不能为空")
        BigDecimal minTemperature,

        @NotNull(message = "允许最高温度不能为空")
        BigDecimal maxTemperature,

        @NotNull(message = "允许最低湿度不能为空")
        @DecimalMin(value = "0", message = "湿度不能小于 0")
        @DecimalMax(value = "100", message = "湿度不能大于 100")
        BigDecimal minHumidity,

        @NotNull(message = "允许最高湿度不能为空")
        @DecimalMin(value = "0", message = "湿度不能小于 0")
        @DecimalMax(value = "100", message = "湿度不能大于 100")
        BigDecimal maxHumidity,

        @NotNull(message = "运输风险等级不能为空")
        RiskLevel transportRisk) {
}
