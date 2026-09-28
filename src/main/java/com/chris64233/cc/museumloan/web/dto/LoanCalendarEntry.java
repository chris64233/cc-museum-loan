package com.chris64233.cc.museumloan.web.dto;

import java.time.LocalDate;

/** 藏品借展日历中的一条已批准占用区间（闭区间）。 */
public record LoanCalendarEntry(
        Long loanRequestId,
        String institutionCode,
        String institutionName,
        LocalDate startDate,
        LocalDate endDate) {
}
