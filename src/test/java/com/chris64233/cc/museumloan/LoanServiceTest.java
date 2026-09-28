package com.chris64233.cc.museumloan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.chris64233.cc.museumloan.domain.Artifact;
import com.chris64233.cc.museumloan.domain.BorrowingInstitution;
import com.chris64233.cc.museumloan.domain.LoanStatus;
import com.chris64233.cc.museumloan.domain.RiskLevel;
import com.chris64233.cc.museumloan.repo.ArtifactRepository;
import com.chris64233.cc.museumloan.repo.BorrowingInstitutionRepository;
import com.chris64233.cc.museumloan.service.IdempotencyService.Outcome;
import com.chris64233.cc.museumloan.service.LoanService;
import com.chris64233.cc.museumloan.web.dto.ArtifactCalendarResponse;
import com.chris64233.cc.museumloan.web.dto.LoanRequestResponse;
import com.chris64233.cc.museumloan.web.dto.SubmitLoanRequest;
import com.chris64233.cc.museumloan.web.error.BadRequestException;
import com.chris64233.cc.museumloan.web.error.ConflictException;

/**
 * 借展核心业务的集成测试：提交校验、整组原子批准、并发争抢、
 * 幂等重放/冲突、拒绝与取消、借展日历。
 */
@SpringBootTest
class LoanServiceTest {

    @Autowired
    private LoanService loanService;

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private BorrowingInstitutionRepository institutionRepository;

    // ------------------------------------------------------------------
    // 测试数据辅助
    // ------------------------------------------------------------------

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private BorrowingInstitution institution(RiskLevel maxRisk) {
        String code = unique("INST");
        return institutionRepository.save(
                new BorrowingInstitution(code, "机构" + code, maxRisk, java.time.Instant.now()));
    }

    private Artifact artifact(boolean loanable, RiskLevel transportRisk) {
        return artifact(loanable, transportRisk,
                bd("18"), bd("24"), bd("45"), bd("55"));
    }

    private Artifact artifact(boolean loanable, RiskLevel transportRisk,
                              BigDecimal minT, BigDecimal maxT, BigDecimal minH, BigDecimal maxH) {
        String no = unique("ART");
        return artifactRepository.save(
                new Artifact(no, "藏品" + no, loanable, minT, maxT, minH, maxH,
                        transportRisk, java.time.Instant.now()));
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private SubmitLoanRequest submitRequest(BorrowingInstitution institution, List<Artifact> artifacts,
                                            LocalDate start, LocalDate end) {
        return new SubmitLoanRequest(institution.getCode(),
                artifacts.stream().map(Artifact::getCatalogNo).toList(),
                start, end, bd("19"), bd("23"), bd("46"), bd("54"), RiskLevel.LOW);
    }

    private String submitAndGetRequestNo(SubmitLoanRequest request) {
        Outcome outcome = loanService.submit(request, unique("idem"));
        assertThat(outcome.replayed()).isFalse();
        return outcome.result().requestNo();
    }

    // ------------------------------------------------------------------
    // 提交校验验
    // ------------------------------------------------------------------

    @Test
    void submitPersistsPendingRequestWithAllItems() {
        BorrowingInstitution inst = institution(RiskLevel.HIGH);
        Artifact a1 = artifact(true, RiskLevel.LOW);
        Artifact a2 = artifact(true, RiskLevel.MEDIUM);

        Outcome outcome = loanService.submit(
                submitRequest(inst, List.of(a1, a2), LocalDate.now().plusDays(10), LocalDate.now().plusDays(20)),
                unique("idem"));

        LoanRequestResponse response = outcome.result();
        assertThat(response.status()).isEqualTo(LoanStatus.PENDING.name());
        assertThat(response.items()).hasSize(2);
        assertThat(response.audits()).hasSize(1);
        assertThat(response.audits().get(0).action()).isEqualTo("SUBMITTED");
        assertThat(response.audits().get(0).toStatus()).isEqualTo(LoanStatus.PENDING.name());
    }

    @Test
    void submitRejectsDuplicateCatalogNos() {
        BorrowingInstitution inst = institution(RiskLevel.HIGH);
        Artifact a = artifact(true, RiskLevel.LOW);
        SubmitLoanRequest request = new SubmitLoanRequest(inst.getCode(),
                List.of(a.getCatalogNo(), a.getCatalogNo()),
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(20),
                bd("19"), bd("23"), bd("46"), bd("54"), RiskLevel.LOW);

        assertThatThrownBy(() -> loanService.submit(request, unique("idem")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("重复藏品号");
    }

    @Test
    void submitRejectsUnknownArtifactAndInstitution() {
        BorrowingInstitution inst = institution(RiskLevel.HIGH);
        SubmitLoanRequest unknownArtifact = new SubmitLoanRequest(inst.getCode(),
                List.of("NO-SUCH-ART"),
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(20),
                bd("19"), bd("23"), bd("46"), bd("54"), RiskLevel.LOW);
        assertThatThrownBy(() -> loanService.submit(unknownArtifact, unique("idem")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("藏品不存在");

        Artifact a = artifact(true, RiskLevel.LOW);
        SubmitLoanRequest unknownInst = new SubmitLoanRequest("NO-SUCH-INST",
                List.of(a.getCatalogNo()),
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(20),
                bd("19"), bd("23"), bd("46"), bd("54"), RiskLevel.LOW);
        assertThatThrownBy(() -> loanService.submit(unknownInst, unique("idem")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("借展机构不存在");
    }

    @Test
    void submitRejectsInvalidDateAndEnvRanges() {
        BorrowingInstitution inst = institution(RiskLevel.HIGH);
        Artifact a = artifact(true, RiskLevel.LOW);

        SubmitLoanRequest badDates = new SubmitLoanRequest(inst.getCode(),
                List.of(a.getCatalogNo()),
                LocalDate.now().plusDays(20), LocalDate.now().plusDays(10),
                bd("19"), bd("23"), bd("46"), bd("54"), RiskLevel.LOW);
        assertThatThrownBy(() -> loanService.submit(badDates, unique("idem")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("起始日期不能晚于结束日期");

        SubmitLoanRequest badTemp = new SubmitLoanRequest(inst.getCode(),
                List.of(a.getCatalogNo()),
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(20),
                bd("25"), bd("20"), bd("46"), bd("54"), RiskLevel.LOW);
        assertThatThrownBy(() -> loanService.submit(badTemp, unique("idem")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("最低温度不能高于承诺最高温度");
    }

    // ------------------------------------------------------------------
    // 幂等
    // ------------------------------------------------------------------

    @Test
    void submitReplayWithSameKeyAndContentReturnsOriginalResult() {
        BorrowingInstitution inst = institution(RiskLevel.HIGH);
        Artifact a = artifact(true, RiskLevel.LOW);
        SubmitLoanRequest request = submitRequest(inst, List.of(a),
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));
        String key = unique("idem");

        Outcome first = loanService.submit(request, key);
        Outcome replay = loanService.submit(request, key);

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.result().requestNo()).isEqualTo(first.result().requestNo());
        assertThat(replay.result().status()).isEqualTo(LoanStatus.PENDING.name());
    }

    @Test
    void submitSameKeyWithDifferentContentReturns409() {
        BorrowingInstitution inst = institution(RiskLevel.HIGH);
        Artifact a1 = artifact(true, RiskLevel.LOW);
        Artifact a2 = artifact(true, RiskLevel.LOW);
        String key = unique("idem");

        loanService.submit(submitRequest(inst, List.of(a1),
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(20)), key);

        SubmitLoanRequest different = submitRequest(inst, List.of(a2),
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));
        assertThatThrownBy(() -> loanService.submit(different, key))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("幂等键已被不同内容的请求使用");
    }

    @Test
    void approveReplayReturnsOriginalResultWithoutDuplicateAudit() {
        BorrowingInstitution inst = institution(RiskLevel.HIGH);
        Artifact a = artifact(true, RiskLevel.LOW);
        String requestNo = submitAndGetRequestNo(submitRequest(inst, List.of(a),
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(20)));
        String key = unique("idem");

        Outcome first = loanService.approve(requestNo, key);
        Outcome replay = loanService.approve(requestNo, key);

        assertThat(first.result().status()).isEqualTo(LoanStatus.APPROVED.name());
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.result().requestNo()).isEqualTo(requestNo);

        LoanRequestResponse detail = loanService.getDetail(requestNo);
        assertThat(detail.audits())
                .filteredOn(audit -> audit.action().equals("APPROVED"))
                .hasSize(1);
    }

    // ------------------------------------------------------------------
    // 整组原子批准
    // ------------------------------------------------------------------

    @Test
    void approveSucceedsWhenAllConditionsMet() {
        BorrowingInstitution inst = institution(RiskLevel.HIGH);
        Artifact a1 = artifact(true, RiskLevel.LOW);
        Artifact a2 = artifact(true, RiskLevel.MEDIUM);
        String requestNo = submitAndGetRequestNo(submitRequest(inst, List.of(a1, a2),
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(20)));

        Outcome outcome = loanService.approve(requestNo, unique("idem"));

        assertThat(outcome.result().status()).isEqualTo(LoanStatus.APPROVED.name());
        LoanRequestResponse detail = loanService.getDetail(requestNo);
        assertThat(detail.audits()).extracting("action")
                .containsExactly("SUBMITTED", "APPROVED");
    }

    @Test
    void approveFailsWhenArtifactNotLoanable() {
        BorrowingInstitution inst = institution(RiskLevel.HIGH);
        Artifact a = artifact(false, RiskLevel.LOW);
        String requestNo = submitAndGetRequestNo(submitRequest(inst, List.of(a),
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(20)));

        assertThatThrownBy(() -> loanService.approve(requestNo, unique("idem")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("不可外借");
        assertThat(loanService.getDetail(requestNo).status()).isEqualTo(LoanStatus.PENDING.name());
    }

    @Test
    void approveFailsWhenInstitutionRiskInsufficient() {
        BorrowingInstitution inst = institution(RiskLevel.LOW);
        Artifact risky = artifact(true, RiskLevel.HIGH);
        String requestNo = submitAndGetRequestNo(submitRequest(inst, List.of(risky),
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(20)));

        assertThatThrownBy(() -> loanService.approve(requestNo, unique("idem")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("风险能力不足");
    }

    @Test
    void approveFailsWhenTransportPlanRiskExceedsInstitution() {
        BorrowingInstitution inst = institution(RiskLevel.LOW);
        Artifact a = artifact(true, RiskLevel.LOW);
        SubmitLoanRequest request = new SubmitLoanRequest(inst.getCode(),
                List.of(a.getCatalogNo()),
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(20),
                bd("19"), bd("23"), bd("46"), bd("54"), RiskLevel.HIGH);
        String requestNo = submitAndGetRequestNo(request);

        assertThatThrownBy(() -> loanService.approve(requestNo, unique("idem")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("运输方案风险");
    }

    @Test
    void approveFailsWhenPromisedEnvOutsideArtifactRange() {
        BorrowingInstitution inst = institution(RiskLevel.HIGH);
        Artifact a = artifact(true, RiskLevel.LOW, bd("18"), bd("24"), bd("45"), bd("55"));
        SubmitLoanRequest request = new SubmitLoanRequest(inst.getCode(),
                List.of(a.getCatalogNo()),
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(20),
                bd("17"), bd("23"), bd("46"), bd("54"), RiskLevel.LOW);
        String requestNo = submitAndGetRequestNo(request);

        assertThatThrownBy(() -> loanService.approve(requestNo, unique("idem")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("未完全落在藏品");
    }

    @Test
    void approveFailsOnOverlappingApprovedLoanIncludingSameDayHandover() {
        BorrowingInstitution inst = institution(RiskLevel.HIGH);
        Artifact a = artifact(true, RiskLevel.LOW);
        LocalDate start = LocalDate.now().plusDays(10);
        LocalDate end = LocalDate.now().plusDays(20);

        String first = submitAndGetRequestNo(submitRequest(inst, List.of(a), start, end));
        loanService.approve(first, unique("idem"));

        // 完全重叠
        String overlapping = submitAndGetRequestNo(submitRequest(inst, List.of(a),
                start.plusDays(2), end.minusDays(2)));
        assertThatThrownBy(() -> loanService.approve(overlapping, unique("idem")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("日期重叠");

        // 同一天交接（前一借展结束日 == 后一借展开始日）也视为冲突
        String sameDayHandover = submitAndGetRequestNo(submitRequest(inst, List.of(a),
                end, end.plusDays(10)));
        assertThatThrownBy(() -> loanService.approve(sameDayHandover, unique("idem")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("日期重叠");

        // 紧邻但不重叠（结束日次日开始）可以批准
        String adjacent = submitAndGetRequestNo(submitRequest(inst, List.of(a),
                end.plusDays(1), end.plusDays(10)));
        loanService.approve(adjacent, unique("idem"));
        assertThat(loanService.getDetail(adjacent).status()).isEqualTo(LoanStatus.APPROVED.name());
    }

    @Test
    void approveIsAtomicAcrossWholeGroup() {
        BorrowingInstitution inst = institution(RiskLevel.HIGH);
        Artifact good = artifact(true, RiskLevel.LOW);
        Artifact notLoanable = artifact(false, RiskLevel.LOW);
        String requestNo = submitAndGetRequestNo(submitRequest(inst, List.of(good, notLoanable),
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(20)));

        // 组内一件不可外借 → 整组失败，申请保持待审批
        assertThatThrownBy(() -> loanService.approve(requestNo, unique("idem")))
                .isInstanceOf(ConflictException.class);
        assertThat(loanService.getDetail(requestNo).status()).isEqualTo(LoanStatus.PENDING.name());

        // 可外借的那件没有被预留：另一申请可立即批准同一日期区间
        String other = submitAndGetRequestNo(submitRequest(inst, List.of(good),
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(20)));
        loanService.approve(other, unique("idem"));
        assertThat(loanService.getDetail(other).status()).isEqualTo(LoanStatus.APPROVED.name());
    }

    @Test
    void concurrentApprovesCompetingForSameArtifactOnlyOneSucceeds() throws Exception {
        BorrowingInstitution inst = institution(RiskLevel.HIGH);
        Artifact shared = artifact(true, RiskLevel.LOW);
        LocalDate start = LocalDate.now().plusDays(10);
        LocalDate end = LocalDate.now().plusDays(20);

        String requestA = submitAndGetRequestNo(submitRequest(inst, List.of(shared), start, end));
        String requestB = submitAndGetRequestNo(submitRequest(inst, List.of(shared), start, end));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<Outcome> futureA = pool.submit(() -> {
                ready.countDown();
                go.await();
                return loanService.approve(requestA, unique("idem"));
            });
            Future<Outcome> futureB = pool.submit(() -> {
                ready.countDown();
                go.await();
                return loanService.approve(requestB, unique("idem"));
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            int successes = 0;
            int conflicts = 0;
            for (Future<Outcome> future : List.of(futureA, futureB)) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                    successes++;
                } catch (java.util.concurrent.ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(ConflictException.class);
                    conflicts++;
                }
            }
            assertThat(successes).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        // 最终只有一个申请处于已批准状态
        List<LoanStatus> statuses = List.of(
                LoanStatus.valueOf(loanService.getDetail(requestA).status()),
                LoanStatus.valueOf(loanService.getDetail(requestB).status()));
        assertThat(statuses).containsExactlyInAnyOrder(LoanStatus.APPROVED, LoanStatus.PENDING);
    }

    // ------------------------------------------------------------------
    // 拒绝与取消
    // ------------------------------------------------------------------

    @Test
    void rejectRecordsReasonAndAudit() {
        BorrowingInstitution inst = institution(RiskLevel.HIGH);
        Artifact a = artifact(true, RiskLevel.LOW);
        String requestNo = submitAndGetRequestNo(submitRequest(inst, List.of(a),
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(20)));

        Outcome outcome = loanService.reject(requestNo, "保护条件不达标", unique("idem"));

        assertThat(outcome.result().status()).isEqualTo(LoanStatus.REJECTED.name());
        assertThat(outcome.result().reason()).isEqualTo("保护条件不达标");
        LoanRequestResponse detail = loanService.getDetail(requestNo);
        assertThat(detail.audits()).extracting("action")
                .containsExactly("SUBMITTED", "REJECTED");
        assertThat(detail.audits().get(1).reason()).isEqualTo("保护条件不达标");

        // 已拒绝的申请不能再批准
        assertThatThrownBy(() -> loanService.approve(requestNo, unique("idem")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cancelOnlyAllowedForApprovedAndNotStarted() {
        BorrowingInstitution inst = institution(RiskLevel.HIGH);
        Artifact future = artifact(true, RiskLevel.LOW);
        Artifact started = artifact(true, RiskLevel.LOW);

        // 待审批不能取消
        String pending = submitAndGetRequestNo(submitRequest(inst, List.of(future),
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(20)));
        assertThatThrownBy(() -> loanService.cancel(pending, "想取消", unique("idem")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("仅已批准状态");

        // 已批准且未开始 → 可以取消
        String approvedFuture = submitAndGetRequestNo(submitRequest(inst, List.of(future),
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(20)));
        loanService.approve(approvedFuture, unique("idem"));
        Outcome cancelled = loanService.cancel(approvedFuture, "借展方行程变更", unique("idem"));
        assertThat(cancelled.result().status()).isEqualTo(LoanStatus.CANCELLED.name());
        assertThat(cancelled.result().reason()).isEqualTo("借展方行程变更");

        // 已批准但已开始（起始日期为今天）→ 不能取消
        String approvedStarted = submitAndGetRequestNo(submitRequest(inst, List.of(started),
                LocalDate.now(), LocalDate.now().plusDays(10)));
        loanService.approve(approvedStarted, unique("idem"));
        assertThatThrownBy(() -> loanService.cancel(approvedStarted, "太晚了", unique("idem")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("已开始");
    }

    @Test
    void cancelReleasesAllArtifactsForReuse() {
        BorrowingInstitution inst = institution(RiskLevel.HIGH);
        Artifact a1 = artifact(true, RiskLevel.LOW);
        Artifact a2 = artifact(true, RiskLevel.LOW);
        LocalDate start = LocalDate.now().plusDays(10);
        LocalDate end = LocalDate.now().plusDays(20);

        String first = submitAndGetRequestNo(submitRequest(inst, List.of(a1, a2), start, end));
        loanService.approve(first, unique("idem"));
        loanService.cancel(first, "取消释放", unique("idem"));

        // 取消后同一日期区间可再次整组批准
        String second = submitAndGetRequestNo(submitRequest(inst, List.of(a1, a2), start, end));
        loanService.approve(second, unique("idem"));
        assertThat(loanService.getDetail(second).status()).isEqualTo(LoanStatus.APPROVED.name());
    }

    // ------------------------------------------------------------------
    // 借展日历
    // ------------------------------------------------------------------

    @Test
    void calendarShowsOnlyApprovedLoansWithinRange() {
        BorrowingInstitution inst = institution(RiskLevel.HIGH);
        Artifact a = artifact(true, RiskLevel.LOW);
        LocalDate base = LocalDate.now().plusDays(30);

        String approved = submitAndGetRequestNo(submitRequest(inst, List.of(a),
                base, base.plusDays(9)));
        loanService.approve(approved, unique("idem"));

        // 待审批与已取消的不出现在日历占用中
        submitAndGetRequestNo(submitRequest(inst, List.of(a), base.plusDays(20), base.plusDays(29)));
        String cancelled = submitAndGetRequestNo(submitRequest(inst, List.of(a),
                base.plusDays(40), base.plusDays(49)));
        loanService.approve(cancelled, unique("idem"));
        loanService.cancel(cancelled, "取消", unique("idem"));

        ArtifactCalendarResponse full = loanService.getCalendar(a.getCatalogNo(), null, null);
        assertThat(full.entries()).hasSize(1);
        assertThat(full.entries().get(0).requestNo()).isEqualTo(approved);
        assertThat(full.entries().get(0).startDate()).isEqualTo(base);
        assertThat(full.entries().get(0).endDate()).isEqualTo(base.plusDays(9));

        // 日期范围过滤：闭区间重叠
        ArtifactCalendarResponse inside = loanService.getCalendar(a.getCatalogNo(),
                base.plusDays(5), base.plusDays(6));
        assertThat(inside.entries()).hasSize(1);
        ArtifactCalendarResponse outside = loanService.getCalendar(a.getCatalogNo(),
                base.plusDays(10), base.plusDays(19));
        assertThat(outside.entries()).isEmpty();
        // 边界日（结束日当天）仍算重叠
        ArtifactCalendarResponse boundary = loanService.getCalendar(a.getCatalogNo(),
                base.plusDays(9), base.plusDays(9));
        assertThat(boundary.entries()).hasSize(1);
    }
}
