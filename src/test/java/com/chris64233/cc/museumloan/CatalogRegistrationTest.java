package com.chris64233.cc.museumloan;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** 基础资料登记的唯一编号约束。 */
class CatalogRegistrationTest extends AbstractLoanIntegrationTest {

    @Test
    void duplicateArtifactCatalogNoReturnsConflict() throws Exception {
        registerDefaultArtifact("A001", "LOW").andExpect(status().isCreated());
        registerDefaultArtifact("A001", "LOW")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("conflict"));
    }

    @Test
    void duplicateInstitutionCodeReturnsConflict() throws Exception {
        registerInstitution("INST-1", "LOW").andExpect(status().isCreated());
        registerInstitution("INST-1", "LOW")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("conflict"));
    }

    @Test
    void invalidArtifactRangeIsRejected() throws Exception {
        registerArtifact("A001", true, "25.00", "15.00", "40.00", "60.00", "LOW")
                .andExpect(status().isBadRequest());
    }
}
