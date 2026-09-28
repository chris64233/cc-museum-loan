package com.chris64233.cc.museumloan;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 批准流程：重检规则、闭区间重叠、整组原子性。 */
class LoanApprovalTest extends AbstractLoanIntegrationTest {

    @Test
    void approveSucceedsAndRecordsAudit() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerDefaultArtifact("A002", "LOW");
        registerInstitution("INST-1", "HIGH");

        long id = applySuccessfully(newKey(), defaultApplyBody(
                "INST-1", List.of("A001", "A002"), "2026-10-01", "2026-10-15", "LOW"));

        approve(id, newKey())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.artifacts.length()").value(2))
                .andExpect(jsonPath("$.auditLogs[1].action").value("APPROVED"));
    }

    @Test
    void committedRangeEqualToAllowedRangeIsAccepted() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerInstitution("INST-1", "HIGH");

        // 承诺区间与允许区间完全相等（边界值应被接受）
        long id = applySuccessfully(newKey(), applyBody("INST-1", List.of("A001"),
                "2026-10-01", "2026-10-15",
                "15.00", "25.00", "40.00", "60.00", "LOW"));

        approve(id, newKey()).andExpect(status().isOk());
    }

    @Test
    void approvingNonPendingRequestReturnsConflict() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerInstitution("INST-1", "HIGH");
        long id = applyAndApprove("INST-1", List.of("A001"), "2026-10-01", "2026-10-15", "LOW");

        approve(id, newKey())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("conflict"));
    }

    @Test
    void approvingUnknownRequestReturnsNotFound() throws Exception {
        approve(999999L, newKey()).andExpect(status().isNotFound());
    }

    @Test
    void artifactTurnedUnloanableAfterApplicationBlocksApproval() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerInstitution("INST-1", "HIGH");
        long id = applySuccessfully(newKey(), defaultApplyBody(
                "INST-1", List.of("A001"), "2026-10-01", "2026-10-15", "LOW"));

        // 申请提交后、批准前藏品被标记为不可外借
        var artifact = artifactRepository.findByCatalogNo("A001").orElseThrow();
        artifact.setLoanable(false);
        artifactRepository.save(artifact);

        approve(id, newKey())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("不可外借")));

        // 整组失败：申请保持待审批
        detail(id).andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void sameDayHandoverCountsAsConflict() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerInstitution("INST-1", "HIGH");
        registerInstitution("INST-2", "HIGH");

        applyAndApprove("INST-1", List.of("A001"), "2026-10-01", "2026-10-10", "LOW");

        // 第二个申请从 10-10 开始：闭区间下同日交接也算冲突
        long second = applySuccessfully(newKey(), defaultApplyBody(
                "INST-2", List.of("A001"), "2026-10-10", "2026-10-20", "LOW"));

        approve(second, newKey())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("日期重叠")));

        detail(second).andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void adjacentNonOverlappingRangeIsAllowed() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerInstitution("INST-1", "HIGH");
        registerInstitution("INST-2", "HIGH");

        applyAndApprove("INST-1", List.of("A001"), "2026-10-01", "2026-10-10", "LOW");

        // 10-11 起借，与已批准的 10-01~10-10 不重叠
        long second = applySuccessfully(newKey(), defaultApplyBody(
                "INST-2", List.of("A001"), "2026-10-11", "2026-10-20", "LOW"));
        approve(second, newKey()).andExpect(status().isOk());
    }

    @Test
    void groupApprovalIsAtomicWhenOneArtifactIsOccupied() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerDefaultArtifact("A002", "LOW");
        registerInstitution("INST-1", "HIGH");
        registerInstitution("INST-2", "HIGH");

        // A001 已被 INST-1 在 10-01~10-10 占用
        applyAndApprove("INST-1", List.of("A001"), "2026-10-01", "2026-10-10", "LOW");

        // INST-2 整组申请 A001+A002，A001 冲突 → 整组失败
        long group = applySuccessfully(newKey(), defaultApplyBody(
                "INST-2", List.of("A001", "A002"), "2026-10-05", "2026-10-15", "LOW"));
        approve(group, newKey()).andExpect(status().isConflict());

        // 原子性：A002 也不能被预留，仍保持待审批
        detail(group).andExpect(jsonPath("$.status").value("PENDING"));

        // A002 未被占用：INST-2 单独申请 A002 重叠日期仍可批准
        long solo = applySuccessfully(newKey(), defaultApplyBody(
                "INST-2", List.of("A002"), "2026-10-05", "2026-10-15", "LOW"));
        approve(solo, newKey()).andExpect(status().isOk());
    }

    @Test
    void rejectedApplicationDoesNotBlockOthers() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerInstitution("INST-1", "HIGH");
        registerInstitution("INST-2", "HIGH");

        long first = applySuccessfully(newKey(), defaultApplyBody(
                "INST-1", List.of("A001"), "2026-10-01", "2026-10-10", "LOW"));
        reject(first, newKey(), "保护条件不满足").andExpect(status().isOk());

        long second = applySuccessfully(newKey(), defaultApplyBody(
                "INST-2", List.of("A001"), "2026-10-01", "2026-10-10", "LOW"));
        approve(second, newKey()).andExpect(status().isOk());
    }
}
