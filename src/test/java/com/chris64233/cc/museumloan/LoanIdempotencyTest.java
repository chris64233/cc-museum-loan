package com.chris64233.cc.museumloan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 幂等键：相同内容重放返回原结果，内容冲突返回 409。 */
class LoanIdempotencyTest extends AbstractLoanIntegrationTest {

    @Test
    void applyReplayWithSameKeyAndContentReturnsOriginalResult() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerInstitution("INST-1", "HIGH");
        var body = defaultApplyBody("INST-1", List.of("A001"),
                "2026-10-01", "2026-10-15", "LOW");
        String key = newKey();

        var first = apply(key, body).andExpect(status().isCreated()).andReturn();
        var second = apply(key, body).andExpect(status().isCreated()).andReturn();

        assertThat(second.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
        // 只创建了一笔申请
        assertThat(loanRequestRepository.count()).isEqualTo(1);
    }

    @Test
    void applyReplayWithSameKeyButDifferentContentReturns409() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerDefaultArtifact("A002", "LOW");
        registerInstitution("INST-1", "HIGH");
        String key = newKey();

        apply(key, defaultApplyBody("INST-1", List.of("A001"),
                "2026-10-01", "2026-10-15", "LOW"))
                .andExpect(status().isCreated());

        apply(key, defaultApplyBody("INST-1", List.of("A002"),
                "2026-10-01", "2026-10-15", "LOW"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("conflict"));
    }

    @Test
    void approveReplayReturnsOriginalResultWithoutDoubleAudit() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerInstitution("INST-1", "HIGH");
        long id = applySuccessfully(newKey(), defaultApplyBody(
                "INST-1", List.of("A001"), "2026-10-01", "2026-10-15", "LOW"));
        String key = newKey();

        var first = approve(id, key).andExpect(status().isOk()).andReturn();
        var second = approve(id, key).andExpect(status().isOk()).andReturn();

        assertThat(second.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
        // 审计记录不因重放而重复
        detail(id).andExpect(jsonPath("$.auditLogs.length()").value(2));
    }

    @Test
    void approveWithReusedKeyOnDifferentLoanReturns409() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerDefaultArtifact("A002", "LOW");
        registerInstitution("INST-1", "HIGH");
        long first = applySuccessfully(newKey(), defaultApplyBody(
                "INST-1", List.of("A001"), "2026-10-01", "2026-10-15", "LOW"));
        long second = applySuccessfully(newKey(), defaultApplyBody(
                "INST-1", List.of("A002"), "2026-10-01", "2026-10-15", "LOW"));
        String key = newKey();

        approve(first, key).andExpect(status().isOk());
        approve(second, key).andExpect(status().isConflict());
    }

    @Test
    void rejectReplayWithDifferentReasonReturns409() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerInstitution("INST-1", "HIGH");
        long id = applySuccessfully(newKey(), defaultApplyBody(
                "INST-1", List.of("A001"), "2026-10-01", "2026-10-15", "LOW"));
        String key = newKey();

        reject(id, key, "原因甲").andExpect(status().isOk());
        reject(id, key, "原因乙").andExpect(status().isConflict());
        reject(id, key, "原因甲").andExpect(status().isOk());
    }

    @Test
    void cancelReplayReturnsOriginalResult() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerInstitution("INST-1", "HIGH");
        long id = applyAndApprove("INST-1", List.of("A001"),
                "2026-10-01", "2026-10-15", "LOW");
        String key = newKey();

        var first = cancel(id, key, "档期调整").andExpect(status().isOk()).andReturn();
        var second = cancel(id, key, "档期调整").andExpect(status().isOk()).andReturn();

        assertThat(second.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
        detail(id).andExpect(jsonPath("$.auditLogs.length()").value(3));
    }

    @Test
    void sameKeyAcrossDifferentOperationsReturns409() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerInstitution("INST-1", "HIGH");
        long id = applySuccessfully(newKey(), defaultApplyBody(
                "INST-1", List.of("A001"), "2026-10-01", "2026-10-15", "LOW"));
        String key = newKey();

        approve(id, key).andExpect(status().isOk());
        // 同一键用于取消操作：操作类型不同即内容冲突
        cancel(id, key, "换个操作").andExpect(status().isConflict());
    }
}
