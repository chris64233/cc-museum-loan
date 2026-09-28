package com.chris64233.cc.museumloan.web;

import com.chris64233.cc.museumloan.service.IdempotentResult;
import com.chris64233.cc.museumloan.service.LoanService;
import com.chris64233.cc.museumloan.service.OperationOutcome;
import com.chris64233.cc.museumloan.exception.ValidationException;
import com.chris64233.cc.museumloan.web.dto.ApplyLoanRequest;
import com.chris64233.cc.museumloan.web.dto.LoanCalendarResponse;
import com.chris64233.cc.museumloan.web.dto.LoanDetailResponse;
import com.chris64233.cc.museumloan.web.dto.ReasonRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    /** 提交借展申请（待审批）。 */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> apply(
            @RequestHeader(name = HttpHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody ApplyLoanRequest request) {
        String key = requireKey(idempotencyKey);
        String contentHash = LoanService.applyContentHash(request);
        IdempotentResult result = loanService.execute(
                LoanService.OP_APPLY, key, contentHash, () -> loanService.apply(request));
        return ResponseEntity.status(result.httpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(result.jsonBody());
    }

    /** 原子批准整组借展。 */
    @PostMapping("/{id}/approval")
    public ResponseEntity<String> approve(
            @PathVariable Long id,
            @RequestHeader(name = HttpHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        String key = requireKey(idempotencyKey);
        IdempotentResult result = loanService.execute(
                LoanService.OP_APPROVE, key, LoanService.approveContentHash(id),
                () -> loanService.approve(id));
        return toResponse(result);
    }

    /** 拒绝申请，必须给出原因。 */
    @PostMapping(value = "/{id}/rejection", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> reject(
            @PathVariable Long id,
            @RequestHeader(name = HttpHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        String key = requireKey(idempotencyKey);
        String contentHash = LoanService.reasonContentHash(id, request.reason());
        IdempotentResult result = loanService.execute(
                LoanService.OP_REJECT, key, contentHash,
                () -> loanService.reject(id, request.reason()));
        return toResponse(result);
    }

    /** 取消尚未开始的已批准借展，释放全部藏品，必须给出原因。 */
    @PostMapping(value = "/{id}/cancellation", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> cancel(
            @PathVariable Long id,
            @RequestHeader(name = HttpHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        String key = requireKey(idempotencyKey);
        String contentHash = LoanService.reasonContentHash(id, request.reason());
        IdempotentResult result = loanService.execute(
                LoanService.OP_CANCEL, key, contentHash,
                () -> loanService.cancel(id, request.reason()));
        return toResponse(result);
    }

    /** 申请详情（含藏品清单与审计记录）。 */
    @GetMapping("/{id}")
    public LoanDetailResponse detail(@PathVariable Long id) {
        return loanService.getDetail(id);
    }

    /** 藏品借展日历：该藏品全部已批准占用区间（闭区间）。 */
    @GetMapping("/artifacts/{catalogNo}/calendar")
    public LoanCalendarResponse calendar(@PathVariable String catalogNo) {
        return loanService.getCalendar(catalogNo);
    }

    private String requireKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationException("状态操作必须提供 " + HttpHeaders.IDEMPOTENCY_KEY + " 请求头");
        }
        return idempotencyKey.trim();
    }

    private ResponseEntity<String> toResponse(IdempotentResult result) {
        return ResponseEntity.status(result.httpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(result.jsonBody());
    }
}
