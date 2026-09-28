package com.chris64233.cc.museumloan;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 拒绝与取消：原因保留、审计记录、取消窗口与藏品释放。 */
class LoanRejectCancelTest extends AbstractLoanIntegrationTest {

    @Test
    void rejectKeepsReasonAndAudit() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerInstitution("INST-1", "HIGH");
        long id = applySuccessfully(newKey(), defaultApplyBody(
                "INST-1", List.of("A001"), "2026-10-01", "2026-10-15", "LOW"));

        reject(id, newKey(), "展柜恒温设备未达标")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value("展柜恒温设备未达标"));

        detail(id)
                .andExpect(jsonPath("$.auditLogs[0].action").value("SUBMITTED"))
                .andExpect(jsonPath("$.auditLogs[1].action").value("REJECTED"))
                .andExpect(jsonPath("$.auditLogs[1].reason").value("展柜恒温设备未达标"));
    }

    @Test
    void rejectRequiresReason() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerInstitution("INST-1", "HIGH");
        long id = applySuccessfully(newKey(), defaultApplyBody(
                "INST-1", List.of("A001"), "2026-10-01", "2026-10-15", "LOW"));

        postJson("/api/loans/" + id + "/rejection", java.util.Map.of("reason", " "), newKey())
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotRejectApprovedRequest() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerInstitution("INST-1", "HIGH");
        long id = applyAndApprove("INST-1", List.of("A001"), "2026-10-01", "2026-10-15", "LOW");

        reject(id, newKey(), "太迟了").andExpect(status().isConflict());
    }

    @Test
    void cancelApprovedLoanReleasesAllArtifactsAndKeepsAudit() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerDefaultArtifact("A002", "LOW");
        registerInstitution("INST-1", "HIGH");
        registerInstitution("INST-2", "HIGH");

        long id = applyAndApprove("INST-1", List.of("A001", "A002"),
                "2026-10-01", "2026-10-15", "LOW");

        cancel(id, newKey(), "借展方档期调整")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.reason").value("借展方档期调整"));

        detail(id)
                .andExpect(jsonPath("$.auditLogs[2].action").value("CANCELLED"))
                .andExpect(jsonPath("$.auditLogs[2].reason").value("借展方档期调整"));

        // 全部藏品被释放：同区间可再次被其他机构批准
        long second = applySuccessfully(newKey(), defaultApplyBody(
                "INST-2", List.of("A001", "A002"), "2026-10-01", "2026-10-15", "LOW"));
        approve(second, newKey()).andExpect(status().isOk());
    }

    @Test
    void cannotCancelLoanThatHasStarted() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerInstitution("INST-1", "HIGH");

        // 开始日期为今天：借展已开始，不能取消
        String today = LocalDate.now().toString();
        String tomorrow = LocalDate.now().plusDays(1).toString();
        long id = applyAndApprove("INST-1", List.of("A001"), today, tomorrow, "LOW");

        cancel(id, newKey(), "想取消")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("不能取消")));

        detail(id).andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void cannotCancelPendingRequest() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerInstitution("INST-1", "HIGH");
        long id = applySuccessfully(newKey(), defaultApplyBody(
                "INST-1", List.of("A001"), "2026-10-01", "2026-10-15", "LOW"));

        cancel(id, newKey(), "还没批呢").andExpect(status().isConflict());
    }

    @Test
    void cannotCancelTwice() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerInstitution("INST-1", "HIGH");
        long id = applyAndApprove("INST-1", List.of("A001"), "2026-10-01", "2026-10-15", "LOW");

        cancel(id, newKey(), "第一次取消").andExpect(status().isOk());
        cancel(id, newKey(), "第二次取消").andExpect(status().isConflict());
    }

    @Test
    void cancelUnknownRequestReturnsNotFound() throws Exception {
        cancel(999999L, newKey(), "不存在").andExpect(status().isNotFound());
    }
}
