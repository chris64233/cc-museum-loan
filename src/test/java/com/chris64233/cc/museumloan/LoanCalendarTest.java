package com.chris64233.cc.museumloan;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 藏品借展日历查询。 */
class LoanCalendarTest extends AbstractLoanIntegrationTest {

    @Test
    void calendarListsApprovedLoansInDateOrder() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerInstitution("INST-1", "HIGH");
        registerInstitution("INST-2", "HIGH");

        applyAndApprove("INST-2", List.of("A001"), "2026-11-01", "2026-11-10", "LOW");
        applyAndApprove("INST-1", List.of("A001"), "2026-10-01", "2026-10-10", "LOW");

        calendar("A001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifactCatalogNo").value("A001"))
                .andExpect(jsonPath("$.approvedLoans.length()").value(2))
                .andExpect(jsonPath("$.approvedLoans[0].institutionCode").value("INST-1"))
                .andExpect(jsonPath("$.approvedLoans[0].startDate").value("2026-10-01"))
                .andExpect(jsonPath("$.approvedLoans[0].endDate").value("2026-10-10"))
                .andExpect(jsonPath("$.approvedLoans[1].institutionCode").value("INST-2"));
    }

    @Test
    void pendingAndRejectedLoansDoNotAppearOutsideCalendar() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerInstitution("INST-1", "HIGH");

        long pending = applySuccessfully(newKey(), defaultApplyBody(
                "INST-1", List.of("A001"), "2026-10-01", "2026-10-10", "LOW"));
        long rejected = applySuccessfully(newKey(), defaultApplyBody(
                "INST-1", List.of("A001"), "2026-11-01", "2026-11-10", "LOW"));
        reject(rejected, newKey(), "条件不满足").andExpect(status().isOk());

        calendar("A001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvedLoans.length()").value(0));
    }

    @Test
    void cancelledLoanIsRemovedFromCalendar() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerInstitution("INST-1", "HIGH");

        long id = applyAndApprove("INST-1", List.of("A001"), "2026-10-01", "2026-10-10", "LOW");
        calendar("A001").andExpect(jsonPath("$.approvedLoans.length()").value(1));

        cancel(id, newKey(), "档期调整").andExpect(status().isOk());
        calendar("A001").andExpect(jsonPath("$.approvedLoans.length()").value(0));
    }

    @Test
    void calendarOfUnknownArtifactReturnsNotFound() throws Exception {
        calendar("NOPE").andExpect(status().isNotFound());
    }

    @Test
    void detailOfUnknownLoanReturnsNotFound() throws Exception {
        detail(999999L).andExpect(status().isNotFound());
    }
}
