package com.chris64233.cc.museumloan.web;

import java.net.URI;
import java.time.LocalDate;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.cc.museumloan.service.IdempotencyService.Outcome;
import com.chris64233.cc.museumloan.service.LoanService;
import com.chris64233.cc.museumloan.web.dto.ArtifactCalendarResponse;
import com.chris64233.cc.museumloan.web.dto.LoanRequestResponse;
import com.chris64233.cc.museumloan.web.dto.ReasonRequest;
import com.chris64233.cc.museumloan.web.dto.SubmitLoanRequest;

/**
 * 借展申请接口。
 *
 * <p>所有状态操作（提交/批准/拒绝/取消）均要求携带 {@code Idempotency-Key} 请求头：
 * 相同键 + 相同内容重放返回首次结果；相同键 + 不同内容返回 409。
 */
@RestController
@RequestMapping("/api/loan-requests")
public class LoanRequestController {

    private final LoanService loanService;

    public LoanRequestController(LoanService loanService) {
        this.loanService = loanService;
    }

    /** 提交借展申请（一次可含多件藏品），保存为待审批状态。 */
    @PostMapping
    public ResponseEntity<LoanRequestResponse> submit(
            @Valid @RequestBody SubmitLoanRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        Outcome outcome = loanService.submit(request, idempotencyKey);
        LoanRequestResponse body = outcome.result();
        ResponseEntity.BodyBuilder builder = outcome.replayed()
                ? ResponseEntity.ok()
                : ResponseEntity.created(URI.create("/api/loan-requests/" + body.requestNo()));
        return builder.body(body);
    }

    /** 整组原子批准：全部藏品满足条件才批准，否则整组失败。 */
    @PostMapping("/{requestNo}/approve")
    public LoanRequestResponse approve(@PathVariable String requestNo,
                                       @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return loanService.approve(requestNo, idempotencyKey).result();
    }

    /** 拒绝待审批申请，必须给出原因。 */
    @PostMapping("/{requestNo}/reject")
    public LoanRequestResponse reject(@PathVariable String requestNo,
                                      @Valid @RequestBody ReasonRequest request,
                                      @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return loanService.reject(requestNo, request.reason(), idempotencyKey).result();
    }

    /** 取消尚未开始的已批准借展，释放全部藏品，必须给出原因。 */
    @PostMapping("/{requestNo}/cancel")
    public LoanRequestResponse cancel(@PathVariable String requestNo,
                                      @Valid @RequestBody ReasonRequest request,
                                      @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return loanService.cancel(requestNo, request.reason(), idempotencyKey).result();
    }

    /** 申请详情（含藏品行与审计记录）。 */
    @GetMapping("/{requestNo}")
    public LoanRequestResponse detail(@PathVariable String requestNo) {
        return loanService.getDetail(requestNo);
    }

    /** 单件藏品的借展日历，可选日期范围过滤（闭区间重叠）。 */
    @GetMapping("/calendar/{catalogNo}")
    public ArtifactCalendarResponse calendar(
            @PathVariable String catalogNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return loanService.getCalendar(catalogNo, from, to);
    }
}
