package com.chris64233.cc.museumloan.web.dto;

import java.util.List;

public record LoanCalendarResponse(
        String artifactCatalogNo,
        String artifactName,
        List<LoanCalendarEntry> approvedLoans) {
}
