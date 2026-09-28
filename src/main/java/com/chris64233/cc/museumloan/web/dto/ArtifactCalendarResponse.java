package com.chris64233.cc.museumloan.web.dto;

import java.time.LocalDate;
import java.util.List;

/** 单件藏品的借展日历：当前已批准（含未开始）的借展占用区间。 */
public record ArtifactCalendarResponse(

        String catalogNo,
        String artifactName,
        boolean loanable,
        List<Entry> entries) {

    public record Entry(String requestNo,
                        String institutionCode,
                        String institutionName,
                        LocalDate startDate,
                        LocalDate endDate,
                        String status) {
    }
}
