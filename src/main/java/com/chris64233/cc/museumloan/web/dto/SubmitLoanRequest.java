package com.chris64233.cc.museumloan.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.chris64233.cc.museumloan.domain.RiskLevel;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 借展申请提交请求。一次申请可包含多件藏品（藏品号不可重复，在业务层校验）。
 *
 * @param catalogNos 藏品号列表，至少一件，且不允许重复
 */
public record SubmitLoanRequest(

        @NotBlank(message = "机构编号不能为空")
        @Size(max = 64)
        String institutionCode,

        @NotEmpty(message = "申请至少包含一件藏品")
        List<@NotBlank(message = "藏品号不能为空") @Size(max = 64) String> catalogNos,

        @NotNull(message = "借展起始日期不能为空")
        LocalDate startDate,

        @NotNull(message = "借展结束日期不能为空")
        LocalDate endDate,

        @NotNull(message = "承诺最低温度不能为空")
        BigDecimal promisedMinTemperature,

        @NotNull(message = "承诺最高温度不能为空")
        BigDecimal promisedMaxTemperature,

        @NotNull(message = "承诺最低湿度不能为空")
        @DecimalMin(value = "0", message = "湿度不能小于 0")
        @DecimalMax(value = "100", message = "湿度不能大于 100")
        BigDecimal promisedMinHumidity,

        @NotNull(message = "承诺最高湿度不能为空")
        @DecimalMin(value = "0", message = "湿度不能小于 0")
        @DecimalMax(value = "100", message = "湿度不能大于 100")
        BigDecimal promisedMaxHumidity,

        @NotNull(message = "运输方案风险等级不能为空")
        RiskLevel transportPlanRisk) {
}
