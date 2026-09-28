package com.chris64233.cc.museumloan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** 并发争抢同一藏品：整组批准至多一个成功。 */
class LoanConcurrencyTest extends AbstractLoanIntegrationTest {

    @Test
    void concurrentApprovalsCompetingForSameArtifactAllowAtMostOneWinner() throws Exception {
        registerDefaultArtifact("SHARED", "LOW");
        registerDefaultArtifact("ONLY-A", "LOW");
        registerDefaultArtifact("ONLY-B", "LOW");
        registerInstitution("INST-1", "HIGH");
        registerInstitution("INST-2", "HIGH");

        long loanA = applySuccessfully(newKey(), defaultApplyBody(
                "INST-1", List.of("SHARED", "ONLY-A"), "2026-10-01", "2026-10-15", "LOW"));
        long loanB = applySuccessfully(newKey(), defaultApplyBody(
                "INST-2", List.of("SHARED", "ONLY-B"), "2026-10-01", "2026-10-15", "LOW"));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> statusA = pool.submit(() -> {
                ready.countDown();
                go.await(5, TimeUnit.SECONDS);
                return approve(loanA, newKey()).andReturn().getResponse().getStatus();
            });
            Future<Integer> statusB = pool.submit(() -> {
                ready.countDown();
                go.await(5, TimeUnit.SECONDS);
                return approve(loanB, newKey()).andReturn().getResponse().getStatus();
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            int a = statusA.get(30, TimeUnit.SECONDS);
            int b = statusB.get(30, TimeUnit.SECONDS);

            // 至多一个成功；失败方整组失败
            assertThat(List.of(a, b)).containsExactlyInAnyOrder(200, 409);

            long winner = a == 200 ? loanA : loanB;
            long loser = a == 200 ? loanB : loanA;
            detail(winner).andExpect(jsonPath("$.status").value("APPROVED"));
            detail(loser).andExpect(jsonPath("$.status").value("PENDING"));

            // 共享藏品只被胜方占用
            calendar("SHARED").andExpect(jsonPath("$.approvedLoans.length()").value(1));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentApprovalsOnDisjointArtifactsBothSucceed() throws Exception {
        registerDefaultArtifact("A001", "LOW");
        registerDefaultArtifact("A002", "LOW");
        registerInstitution("INST-1", "HIGH");
        registerInstitution("INST-2", "HIGH");

        long loanA = applySuccessfully(newKey(), defaultApplyBody(
                "INST-1", List.of("A001"), "2026-10-01", "2026-10-15", "LOW"));
        long loanB = applySuccessfully(newKey(), defaultApplyBody(
                "INST-2", List.of("A002"), "2026-10-01", "2026-10-15", "LOW"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> statusA = pool.submit(() -> approve(loanA, newKey()).andReturn().getResponse().getStatus());
            Future<Integer> statusB = pool.submit(() -> approve(loanB, newKey()).andReturn().getResponse().getStatus());
            assertThat(statusA.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(statusB.get(30, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            pool.shutdownNow();
        }
    }
}
