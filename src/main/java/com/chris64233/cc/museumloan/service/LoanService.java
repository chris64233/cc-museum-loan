package com.chris64233.cc.museumloan.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.cc.museumloan.domain.Artifact;
import com.chris64233.cc.museumloan.domain.AuditAction;
import com.chris64233.cc.museumloan.domain.AuditRecord;
import com.chris64233.cc.museumloan.domain.BorrowingInstitution;
import com.chris64233.cc.museumloan.domain.IdempotentOperation;
import com.chris64233.cc.museumloan.domain.LoanRequest;
import com.chris64233.cc.museumloan.domain.LoanRequestItem;
import com.chris64233.cc.museumloan.domain.LoanStatus;
import com.chris64233.cc.museumloan.repo.ArtifactRepository;
import com.chris64233.cc.museumloan.repo.AuditRecordRepository;
import com.chris64233.cc.museumloan.repo.BorrowingInstitutionRepository;
import com.chris64233.cc.museumloan.repo.LoanRequestItemRepository;
import com.chris64233.cc.museumloan.repo.LoanRequestRepository;
import com.chris64233.cc.museumloan.service.IdempotencyService.Execution;
import com.chris64233.cc.museumloan.service.IdempotencyService.Outcome;
import com.chris64233.cc.museumloan.web.dto.ArtifactCalendarResponse;
import com.chris64233.cc.museumloan.web.dto.LoanRequestResponse;
import com.chris64233.cc.museumloan.web.dto.SubmitLoanRequest;
import com.chris64233.cc.museumloan.web.error.BadRequestException;
import com.chris64233.cc.museumloan.web.error.ConflictException;
import com.chris64233.cc.museumloan.web.error.NotFoundException;

/**
 * 借展申请核心服务：提交、整组原子批准、拒绝、取消与查询。
 *
 * <p>并发与原子性设计：
 * <ul>
 *     <li>批准时对申请行加写锁（{@code PESSIMISTIC_WRITE}），再按 id 升序对全部
 *         目标藏品行加写锁，两个争抢同一藏品的批准事务在此串行化；后到者在
 *         重叠检查中看到先到者已批准的借展而整组失败；</li>
 *     <li>批准的全部校验与状态变更在单个事务内完成，任一藏品不满足条件即抛异常
 *         整体回滚，不存在“只预留部分藏品”的中间态；</li>
 *     <li>藏品占用完全由“已批准且日期重叠”的申请推导，取消已批准申请即自动释放
 *         其全部藏品，无需额外的预留表。</li>
 * </ul>
 */
@Service
public class LoanService {

    private final LoanRequestRepository loanRequestRepository;
    private final LoanRequestItemRepository itemRepository;
    private final ArtifactRepository artifactRepository;
    private final BorrowingInstitutionRepository institutionRepository;
    private final AuditRecordRepository auditRepository;
    private final IdempotencyService idempotencyService;
    private final Clock clock;

    public LoanService(LoanRequestRepository loanRequestRepository,
                       LoanRequestItemRepository itemRepository,
                       ArtifactRepository artifactRepository,
                       BorrowingInstitutionRepository institutionRepository,
                       AuditRecordRepository auditRepository,
                       IdempotencyService idempotencyService,
                       Clock clock) {
        this.loanRequestRepository = loanRequestRepository;
        this.itemRepository = itemRepository;
        this.artifactRepository = artifactRepository;
        this.institutionRepository = institutionRepository;
        this.auditRepository = auditRepository;
        this.idempotencyService = idempotencyService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // 提交申请
    // ------------------------------------------------------------------

    /** 提交借展申请（幂等）。完整校验申请内容后保存为待审批状态。 */
    public Outcome submit(SubmitLoanRequest request, String idempotencyKey) {
        String hash = IdempotencyService.sha256(canonicalSubmitContent(request));
        return idempotencyService.execute(IdempotentOperation.SUBMIT, idempotencyKey, hash,
                () -> doSubmit(request));
    }

    private Execution doSubmit(SubmitLoanRequest request) {
        List<String> problems = new ArrayList<>();

        // 结构校验：日期与温湿度区间自洽
        if (request.startDate().isAfter(request.endDate())) {
            problems.add("借展起始日期不能晚于结束日期");
        }
        if (request.promisedMinTemperature().compareTo(request.promisedMaxTemperature()) > 0) {
            problems.add("承诺最低温度不能高于承诺最高温度");
        }
        if (request.promisedMinHumidity().compareTo(request.promisedMaxHumidity()) > 0) {
            problems.add("承诺最低湿度不能高于承诺最高湿度");
        }

        // 藏品号去重校验（保留首次出现顺序用于报错）
        List<String> catalogNos = request.catalogNos();
        var seen = new LinkedHashSet<String>();
        var duplicates = new LinkedHashSet<String>();
        for (String no : catalogNos) {
            if (!seen.add(no)) {
                duplicates.add(no);
            }
        }
        if (!duplicates.isEmpty()) {
            problems.add("申请包含重复藏品号: " + String.join(", ", duplicates));
        }

        BorrowingInstitution institution = institutionRepository.findByCode(request.institutionCode())
                .orElse(null);
        if (institution == null) {
            problems.add("借展机构不存在: " + request.institutionCode());
        }

        Map<String, Artifact> artifacts = catalogNos.stream().distinct()
                .map(no -> artifactRepository.findByCatalogNo(no).orElse(null))
                .filter(a -> a != null)
                .collect(Collectors.toMap(Artifact::getCatalogNo, Function.identity()));
        for (String no : catalogNos.stream().distinct().toList()) {
            if (!artifacts.containsKey(no)) {
                problems.add("藏品不存在: " + no);
            }
        }

        if (!problems.isEmpty()) {
            throw new BadRequestException("借展申请内容校验失败", problems);
        }

        Instant now = Instant.now(clock);
        LoanRequest loanRequest = new LoanRequest(newRequestNo(), institution,
                request.startDate(), request.endDate(),
                request.promisedMinTemperature(), request.promisedMaxTemperature(),
                request.promisedMinHumidity(), request.promisedMaxHumidity(),
                request.transportPlanRisk(), now);
        for (String no : catalogNos.stream().distinct().toList()) {
            loanRequest.addItem(new LoanRequestItem(loanRequest, artifacts.get(no)));
        }
        loanRequestRepository.save(loanRequest);
        auditRepository.save(new AuditRecord(loanRequest, AuditAction.SUBMITTED,
                null, LoanStatus.PENDING, null, now));
        return new Execution(toResponse(loanRequest), loanRequest.getRequestNo());
    }

    // ------------------------------------------------------------------
    // 整组原子批准
    // ------------------------------------------------------------------

    /** 批准申请（幂等）。整组全部成功或全部失败。 */
    public Outcome approve(String requestNo, String idempotencyKey) {
        String hash = IdempotencyService.sha256("APPROVE|" + requestNo);
        return idempotencyService.execute(IdempotentOperation.APPROVE, idempotencyKey, hash,
                () -> doApprove(requestNo));
    }

    private Execution doApprove(String requestNo) {
        LoanRequest loanRequest = loanRequestRepository.findByRequestNoForUpdate(requestNo)
                .orElseThrow(() -> new NotFoundException("借展申请不存在: " + requestNo));
        if (loanRequest.getStatus() != LoanStatus.PENDING) {
            throw new ConflictException("仅待审批状态的申请可以批准",
                    List.of("当前状态: " + loanRequest.getStatus()));
        }

        // 按 id 升序对全部目标藏品加行级写锁，串行化并发争抢
        List<Long> artifactIds = loanRequest.getItems().stream()
                .map(item -> item.getArtifact().getId())
                .sorted()
                .toList();
        List<Artifact> lockedArtifacts = artifactRepository.findByIdInForUpdate(artifactIds);

        List<String> problems = new ArrayList<>();
        BorrowingInstitution institution = loanRequest.getInstitution();
        for (Artifact artifact : lockedArtifacts) {
            String no = artifact.getCatalogNo();

            // 1. 藏品当前可外借
            if (!artifact.isLoanable()) {
                problems.add("藏品 " + no + " 当前不可外借");
            }

            // 2. 机构风险能力足够：需同时覆盖藏品运输风险与运输方案风险
            if (!institution.getMaxRisk().covers(artifact.getTransportRisk())) {
                problems.add("机构风险能力不足：藏品 " + no + " 运输风险 "
                        + artifact.getTransportRisk() + " 超过机构可承担上限 " + institution.getMaxRisk());
            }
            if (!institution.getMaxRisk().covers(loanRequest.getTransportPlanRisk())) {
                problems.add("机构风险能力不足：运输方案风险 " + loanRequest.getTransportPlanRisk()
                        + " 超过机构可承担上限 " + institution.getMaxRisk());
            }

            // 3. 承诺温湿度区间完全落在藏品允许范围内（闭区间）
            if (loanRequest.getPromisedMinTemperature().compareTo(artifact.getMinTemperature()) < 0
                    || loanRequest.getPromisedMaxTemperature().compareTo(artifact.getMaxTemperature()) > 0) {
                problems.add("承诺温度区间 [" + loanRequest.getPromisedMinTemperature() + ", "
                        + loanRequest.getPromisedMaxTemperature() + "] 未完全落在藏品 " + no
                        + " 允许范围 [" + artifact.getMinTemperature() + ", "
                        + artifact.getMaxTemperature() + "] 内");
            }
            if (loanRequest.getPromisedMinHumidity().compareTo(artifact.getMinHumidity()) < 0
                    || loanRequest.getPromisedMaxHumidity().compareTo(artifact.getMaxHumidity()) > 0) {
                problems.add("承诺湿度区间 [" + loanRequest.getPromisedMinHumidity() + ", "
                        + loanRequest.getPromisedMaxHumidity() + "] 未完全落在藏品 " + no
                        + " 允许范围 [" + artifact.getMinHumidity() + ", "
                        + artifact.getMaxHumidity() + "] 内");
            }

            // 4. 不存在日期重叠（闭区间）的已批准借展
            var overlaps = itemRepository.findOverlapping(artifact.getId(), LoanStatus.APPROVED,
                    loanRequest.getId(), loanRequest.getStartDate(), loanRequest.getEndDate());
            for (LoanRequestItem overlap : overlaps) {
                LoanRequest other = overlap.getLoanRequest();
                problems.add("藏品 " + no + " 与已批准借展 " + other.getRequestNo()
                        + " 日期重叠 [" + other.getStartDate() + " ~ " + other.getEndDate() + "]");
            }
        }

        if (!problems.isEmpty()) {
            // 整组失败：抛出异常回滚事务，不预留任何藏品
            throw new ConflictException("借展申请不满足批准条件，整组批准失败", problems);
        }

        Instant now = Instant.now(clock);
        loanRequest.approve(now);
        auditRepository.save(new AuditRecord(loanRequest, AuditAction.APPROVED,
                LoanStatus.PENDING, LoanStatus.APPROVED, null, now));
        return new Execution(toResponse(loanRequest), loanRequest.getRequestNo());
    }

    // ------------------------------------------------------------------
    // 拒绝
    // ------------------------------------------------------------------

    /** 拒绝待审批申请（幂等），记录原因与审计。 */
    public Outcome reject(String requestNo, String reason, String idempotencyKey) {
        String hash = IdempotencyService.sha256("REJECT|" + requestNo + "|" + reason);
        return idempotencyService.execute(IdempotentOperation.REJECT, idempotencyKey, hash,
                () -> doReject(requestNo, reason));
    }

    private Execution doReject(String requestNo, String reason) {
        LoanRequest loanRequest = loanRequestRepository.findByRequestNoForUpdate(requestNo)
                .orElseThrow(() -> new NotFoundException("借展申请不存在: " + requestNo));
        if (loanRequest.getStatus() != LoanStatus.PENDING) {
            throw new ConflictException("仅待审批状态的申请可以拒绝",
                    List.of("当前状态: " + loanRequest.getStatus()));
        }
        Instant now = Instant.now(clock);
        loanRequest.reject(reason, now);
        auditRepository.save(new AuditRecord(loanRequest, AuditAction.REJECTED,
                LoanStatus.PENDING, LoanStatus.REJECTED, reason, now));
        return new Execution(toResponse(loanRequest), loanRequest.getRequestNo());
    }

    // ------------------------------------------------------------------
    // 取消
    // ------------------------------------------------------------------

    /**
     * 取消已批准借展（幂等）。仅允许取消尚未开始（起始日期晚于今天）的已批准借展；
     * 取消后状态置为 CANCELLED，藏品占用由状态推导，全部藏品随之释放。
     */
    public Outcome cancel(String requestNo, String reason, String idempotencyKey) {
        String hash = IdempotencyService.sha256("CANCEL|" + requestNo + "|" + reason);
        return idempotencyService.execute(IdempotentOperation.CANCEL, idempotencyKey, hash,
                () -> doCancel(requestNo, reason));
    }

    private Execution doCancel(String requestNo, String reason) {
        LoanRequest loanRequest = loanRequestRepository.findByRequestNoForUpdate(requestNo)
                .orElseThrow(() -> new NotFoundException("借展申请不存在: " + requestNo));
        if (loanRequest.getStatus() != LoanStatus.APPROVED) {
            throw new ConflictException("仅已批准状态的借展可以取消",
                    List.of("当前状态: " + loanRequest.getStatus()));
        }
        LocalDate today = LocalDate.now(clock);
        if (!loanRequest.getStartDate().isAfter(today)) {
            throw new ConflictException("借展已开始或已结束，不能取消",
                    List.of("借展起始日期: " + loanRequest.getStartDate(), "今天: " + today));
        }
        Instant now = Instant.now(clock);
        loanRequest.cancel(reason, now);
        auditRepository.save(new AuditRecord(loanRequest, AuditAction.CANCELLED,
                LoanStatus.APPROVED, LoanStatus.CANCELLED, reason, now));
        return new Execution(toResponse(loanRequest), loanRequest.getRequestNo());
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /** 申请详情（含藏品行与审计记录）。 */
    @Transactional(readOnly = true)
    public LoanRequestResponse getDetail(String requestNo) {
        LoanRequest loanRequest = loanRequestRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new NotFoundException("借展申请不存在: " + requestNo));
        return toResponse(loanRequest);
    }

    /** 单件藏品的借展日历：已批准借展的占用区间，可按日期范围过滤（闭区间重叠）。 */
    @Transactional(readOnly = true)
    public ArtifactCalendarResponse getCalendar(String catalogNo, LocalDate from, LocalDate to) {
        Artifact artifact = artifactRepository.findByCatalogNo(catalogNo)
                .orElseThrow(() -> new NotFoundException("藏品不存在: " + catalogNo));
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("日历查询起始日期不能晚于结束日期");
        }
        List<ArtifactCalendarResponse.Entry> entries = itemRepository
                .findCalendar(artifact.getId(), LoanStatus.APPROVED, from, to)
                .stream()
                .map(item -> {
                    LoanRequest r = item.getLoanRequest();
                    return new ArtifactCalendarResponse.Entry(r.getRequestNo(),
                            r.getInstitution().getCode(), r.getInstitution().getName(),
                            r.getStartDate(), r.getEndDate(), r.getStatus().name());
                })
                .toList();
        return new ArtifactCalendarResponse(artifact.getCatalogNo(), artifact.getName(),
                artifact.isLoanable(), entries);
    }

    // ------------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------------

    private LoanRequestResponse toResponse(LoanRequest loanRequest) {
        List<LoanRequestResponse.Item> items = loanRequest.getItems().stream()
                .map(item -> new LoanRequestResponse.Item(item.getCatalogNo(),
                        item.getArtifact().getName(),
                        item.getArtifact().getTransportRisk().name()))
                .toList();
        List<LoanRequestResponse.Audit> audits = auditRepository
                .findByLoanRequestIdOrderByOccurredAtAscIdAsc(loanRequest.getId())
                .stream()
                .map(a -> new LoanRequestResponse.Audit(a.getAction().name(),
                        a.getFromStatus() == null ? null : a.getFromStatus().name(),
                        a.getToStatus().name(), a.getReason(), a.getOccurredAt()))
                .toList();
        return new LoanRequestResponse(loanRequest.getRequestNo(), loanRequest.getStatus().name(),
                loanRequest.getInstitution().getCode(), loanRequest.getInstitution().getName(),
                loanRequest.getStartDate(), loanRequest.getEndDate(),
                loanRequest.getPromisedMinTemperature(), loanRequest.getPromisedMaxTemperature(),
                loanRequest.getPromisedMinHumidity(), loanRequest.getPromisedMaxHumidity(),
                loanRequest.getTransportPlanRisk().name(), loanRequest.getReason(),
                loanRequest.getSubmittedAt(), loanRequest.getDecidedAt(), items, audits);
    }

    private String newRequestNo() {
        return "LR-" + UUID.randomUUID();
    }

    /** 提交请求内容的规范化表示，用于幂等哈希。藏品号排序使顺序差异不视为内容冲突。 */
    private String canonicalSubmitContent(SubmitLoanRequest r) {
        return String.join("|",
                r.institutionCode(),
                r.catalogNos().stream().sorted().collect(Collectors.joining(",")),
                r.startDate().toString(), r.endDate().toString(),
                r.promisedMinTemperature().stripTrailingZeros().toPlainString(),
                r.promisedMaxTemperature().stripTrailingZeros().toPlainString(),
                r.promisedMinHumidity().stripTrailingZeros().toPlainString(),
                r.promisedMaxHumidity().stripTrailingZeros().toPlainString(),
                r.transportPlanRisk().name());
    }
}
