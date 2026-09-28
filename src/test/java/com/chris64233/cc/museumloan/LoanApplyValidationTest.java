package com.chris64233.cc.museumloan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 借展申请的完整校验与待审批落库。 */
class LoanApplyValidationTest extends AbstractLoanIntegrationTest {

    @Test
    void validApplicationIsSavedAsPendingWithSubmittedAudit() throws Exception {
        registerDefaultArtifact("A001", "MEDIUM").andExpect(status().isCreated());
        registerInstitution("INST-1", "MEDIUM");

        long id = applySuccessfully(newKey(), defaultApplyBody(
                "INST-1", List.of("A001"), "2026-10-01", "2026-10-15", "LOW"));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/loans/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.artifacts[0].catalogNo").value("A001"))
                .andExpect(jsonPath("$.auditLogs[0].action").value("SUBMITTED"))
                .andExpect(jsonPath("$.decidedAt").doesNotExist());
    }

    @Test
    void duplicateArtifactsInOneApplicationAreRejected() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerDefaultArtifact("A002", "LOW");
        registerInstitution("INST-1", "HIGH");

        apply(newKey(), defaultApplyBody("INST-1", List.of("A001", "A001", "A002"),
                "2026-10-01", "2026-10-15", "LOW"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
    }

    @Test
    void emptyArtifactListIsRejected() throws Exception {
        registerInstitution("INST-1", "HIGH");

        apply(newKey(), defaultApplyBody("INST-1", List.of(),
                "2026-10-01", "2026-10-15", "LOW"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownInstitutionIsRejected() throws Exception {
        registerDefaultArtifact("A001", "LOW");

        apply(newKey(), defaultApplyBody("GHOST", List.of("A001"),
                "2026-10-01", "2026-10-15", "LOW"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownArtifactIsRejected() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerInstitution("INST-1", "HIGH");

        apply(newKey(), defaultApplyBody("INST-1", List.of("A001", "A404"),
                "2026-10-01", "2026-10-15", "LOW"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void endDateBeforeStartDateIsRejected() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerInstitution("INST-1", "HIGH");

        apply(newKey(), defaultApplyBody("INST-1", List.of("A001"),
                "2026-10-15", "2026-10-01", "LOW"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void committedRangeLowerBoundAboveUpperBoundIsRejected() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerInstitution("INST-1", "HIGH");

        apply(newKey(), applyBody("INST-1", List.of("A001"),
                "2026-10-01", "2026-10-15",
                "23.00", "18.00", "45.00", "55.00", "LOW"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void committedEnvironmentOutsideArtifactWindowIsRejected() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerInstitution("INST-1", "HIGH");

        // 藏品允许 15~25℃，承诺 14~22℃：下限越界
        apply(newKey(), applyBody("INST-1", List.of("A001"),
                "2026-10-01", "2026-10-15",
                "14.00", "22.00", "45.00", "55.00", "LOW"))
                .andExpect(status().isBadRequest());
        // 湿度上限越界
        apply(newKey(), applyBody("INST-1", List.of("A001"),
                "2026-10-01", "2026-10-15",
                "18.00", "22.00", "45.00", "61.00", "LOW"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void institutionRiskInsufficientForTransportPlanIsRejected() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerInstitution("INST-1", "MEDIUM");

        apply(newKey(), defaultApplyBody("INST-1", List.of("A001"),
                "2026-10-01", "2026-10-15", "HIGH"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void institutionRiskInsufficientForArtifactRiskIsRejected() throws Exception {
        registerDefaultArtifact("A001", "HIGH");
        registerInstitution("INST-1", "MEDIUM");

        apply(newKey(), defaultApplyBody("INST-1", List.of("A001"),
                "2026-10-01", "2026-10-15", "LOW"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unloanableArtifactCannotBeApplied() throws Exception {
        registerArtifact("A009", false, "15.00", "25.00", "40.00", "60.00", "LOW");
        registerInstitution("INST-1", "HIGH");

        apply(newKey(), defaultApplyBody("INST-1", List.of("A009"),
                "2026-10-01", "2026-10-15", "LOW"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        registerInstitution("INST-1", "HIGH");
        registerDefaultArtifact("A001", "LOW");

        apply(null, defaultApplyBody("INST-1", List.of("A001"),
                "2026-10-01", "2026-10-15", "LOW"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
    }
}
