package com.chris64233.cc.museumloan.service;

import com.chris64233.cc.museumloan.domain.Artifact;
import com.chris64233.cc.museumloan.domain.IdempotencyRecord;
import com.chris64233.cc.museumloan.domain.Institution;
import com.chris64233.cc.museumloan.domain.LoanAuditLog;
import com.chris64233.cc.museumloan.domain.LoanRequest;
import com.chris64233.cc.museumloan.domain.LoanRequestItem;
import com.chris64233.cc.museumloan.domain.LoanStatus;
import com.chris64233.cc.museumloan.exception.ConflictException;
import com.chris64233.cc.museumloan.exception.NotFoundException;
import com.chris64233.cc.museumloan.exception.ValidationException;
import com.chris64233.cc.museumloan.repository.ArtifactRepository;
import com.chris64233.cc.museumloan.repository.IdempotencyRecordRepository;
import com.chris64233.cc.museumloan.repository.InstitutionRepository;
import com.chris64233.cc.museumloan.repository.LoanAuditLogRepository;
import com.chris64233.cc.museumloan.repository.LoanRequestItemRepository;
import com.chris64233.cc.museumloan.repository.LoanRequestRepository;
import com.chris64233.cc.museumloan.web.dto.ApplyLoanRequest;
import com.chris64233.cc.museumloan.web.dto.ArtifactResponse;
import com.chris64233.cc.museumloan.web.dto.AuditLogResponse;
import com.chris64233.cc.museumloan.web.dto.InstitutionResponse;
import com.chris64233.cc.museumloan.web.dto.LoanCalendarEntry;
import com.chris64233.cc.museumloan.web.dto.LoanCalendarResponse;
import com.chris64233.cc.museumloan.web.dto.LoanDetailResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LoanService {

    public static final String OP_APPLY = "APPLY";
    public static final String OP_APPROVE = "APPROVE";
    public static final String OP_REJECT = "REJECT";
    public static final String OP_CANCEL = "CANCEL";

    private final ArtifactRepository artifactRepository;
    private final InstitutionRepository institutionRepository;
    private final LoanRequestRepository loanRequestRepository;
    private final LoanRequestItemRepository itemRepository;
    private final LoanAuditLogRepository auditLogRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public LoanService(ArtifactRepository artifactRepository,
                       InstitutionRepository institutionRepository,
                       LoanRequestRepository loanRequestRepository,
                       LoanRequestItemRepository itemRepository,
                       LoanAuditLogRepository auditLogRepository,
                       IdempotencyRecordRepository idempotencyRepository,
                       PlatformTransactionManager transactionManager,
                       Clock clock) {
        this.artifactRepository = artifactRepository;
        this.institutionRepository = institutionRepository;
        this.loanRequestRepository = loanRequestRepository;
        this.itemRepository = itemRepository;
        this.auditLogRepository = auditLogRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // 幂等执行：相同键+相同内容重放原结果；相同键+不同内容返回 409。
    // ------------------------------------------------------------------

    public IdempotentResult execute(String operation, String idempotencyKey, String contentHash,
                                    Supplier<OperationOutcome> action) {
        Optional<IdempotencyRecord> existing = idempotencyRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), operation, contentHash);
        }
        OperationOutcome outcome;
        try {
            outcome = transactionTemplate.execute(status -> {
                OperationOutcome result = action.get();
                idempotencyRepository.save(new IdempotencyRecord(
                        idempotencyKey, operation, contentHash, result.loanRequestId(),
                        result.httpStatus(), JsonUtil.toJson(result.response())));
                return result;
            });
        } catch (DataIntegrityViolationException e) {
            // 并发下唯一约束兜底：另一事务已提交同键记录。
            IdempotencyRecord winner = idempotencyRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> e);
            return replay(winner, operation, contentHash);
        } catch (RuntimeException e) {
            // 同键并发时失败方可能因行锁等待看到胜方已提交的状态而报错；
            // 此时胜方已落幂等记录，应重放其首次结果而非返回失败。
            IdempotencyRecord winner = idempotencyRepository.findByIdempotencyKey(idempotencyKey)
                    .orElse(null);
            if (winner != null) {
                return replay(winner, operation, contentHash);
            }
            throw e;
        }
        return new IdempotentResult(outcome.httpStatus(), JsonUtil.toJson(outcome.response()), false);
    }

    private IdempotentResult replay(IdempotencyRecord record, String operation, String contentHash) {
        if (!Objects.equals(record.getOperation(), operation)
                || !Objects.equals(record.getContentHash(), contentHash)) {
            throw new ConflictException("幂等键 [" + record.getIdempotencyKey()
                    + "] 已用于不同内容的请求");
        }
        return new IdempotentResult(record.getHttpStatus(), record.getResponseBody(), true);
    }

    // ------------------------------------------------------------------
    // 基础资料登记
    // ------------------------------------------------------------------

    public ArtifactResponse registerArtifact(
            com.chris64233.cc.museumloan.web.dto.RegisterArtifactRequest req) {
        validateRange(req.minTemp(), req.maxTemp(), "允许温度区间");
        validateRange(req.minHumidity(), req.maxHumidity(), "允许湿度区间");
        artifactRepository.findByCatalogNo(req.catalogNo()).ifPresent(a -> {
            throw new ConflictException("藏品号已存在: " + req.catalogNo());
        });
        Artifact artifact = new Artifact(req.catalogNo(), req.name(), req.loanable(),
                req.minTemp(), req.maxTemp(), req.minHumidity(), req.maxHumidity(),
                req.transportRisk());
        return ArtifactResponse.from(artifactRepository.save(artifact));
    }

    public InstitutionResponse registerInstitution(
            com.chris64233.cc.museumloan.web.dto.RegisterInstitutionRequest req) {
        institutionRepository.findByCode(req.code()).ifPresent(i -> {
            throw new ConflictException("机构编号已存在: " + req.code());
        });
        Institution institution = new Institution(req.code(), req.name(), req.maxRisk());
        return InstitutionResponse.from(institutionRepository.save(institution));
    }

    // ------------------------------------------------------------------
    // 借展申请：完整校验后落库为待审批
    // ------------------------------------------------------------------

    public OperationOutcome apply(ApplyLoanRequest req) {
        LocalDate startDate = req.startDate();
        LocalDate endDate = req.endDate();
        if (startDate == null || endDate == null) {
            throw new ValidationException("借展起止日期不能为空");
        }
        if (endDate.isBefore(startDate)) {
            throw new ValidationException("借展结束日期不能早于开始日期");
        }
        validateRange(req.committedMinTemp(), req.committedMaxTemp(), "承诺温度区间");
        validateRange(req.committedMinHumidity(), req.committedMaxHumidity(), "承诺湿度区间");

        Institution institution = institutionRepository.findByCode(req.institutionCode())
                .orElseThrow(() -> new ValidationException("机构不存在: " + req.institutionCode()));

        List<String> catalogNos = req.artifactCatalogNos().stream().distinct().toList();
        if (catalogNos.size() != req.artifactCatalogNos().size()) {
            throw new ValidationException("申请藏品列表存在重复藏品号");
        }
        List<Artifact> artifacts = artifactRepository.findByCatalogNoIn(catalogNos).stream()
                .sorted(java.util.Comparator.comparing(Artifact::getId))
                .toList();
        if (artifacts.size() != catalogNos.size()) {
            throw new ValidationException("部分藏品不存在: " + missingCatalogNos(catalogNos, artifacts));
        }

        // 申请阶段即做完整业务校验，避免明显不合规申请进入待审批队列。
        validateEligibility(artifacts, institution, req.transportRisk(),
                req.committedMinTemp(), req.committedMaxTemp(),
                req.committedMinHumidity(), req.committedMaxHumidity());

        OffsetDateTime now = OffsetDateTime.now(clock);
        LoanRequest loan = new LoanRequest(institution, startDate, endDate,
                req.committedMinTemp(), req.committedMaxTemp(),
                req.committedMinHumidity(), req.committedMaxHumidity(),
                req.transportRisk(), now);
        artifacts.forEach(loan::addItem);
        LoanRequest saved = loanRequestRepository.save(loan);
        auditLogRepository.save(new LoanAuditLog(saved.getId(), "SUBMITTED", null, null, now));
        loanRequestRepository.flush();
        return new OperationOutcome(saved.getId(), 201, detail(saved));
    }

    private List<String> missingCatalogNos(List<String> requested, List<Artifact> found) {
        List<String> foundNos = found.stream().map(Artifact::getCatalogNo).toList();
        return requested.stream().filter(no -> !foundNos.contains(no)).toList();
    }

    // ------------------------------------------------------------------
    // 批准：整组重检，全部通过才原子提交
    // ------------------------------------------------------------------

    public OperationOutcome approve(Long loanId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        LoanRequest loan = getPendingForDecision(loanId);

        // 对整组藏品按固定顺序加悲观行锁，并发争抢同一件藏品时在此串行化，至多一个成功。
        List<Long> artifactIds = loan.getItems().stream()
                .map(i -> i.getArtifact().getId()).sorted().toList();
        List<Artifact> artifacts = artifactRepository.findAllByIdForUpdate(artifactIds);

        List<String> failures = new ArrayList<>();
        for (Artifact artifact : artifacts) {
            if (!artifact.isLoanable()) {
                failures.add("藏品 " + artifact.getCatalogNo() + " 当前不可外借");
                continue;
            }
            List<String> artifactFailures = eligibilityFailures(artifact,
                    loan.getInstitution(), loan.getTransportRisk(),
                    loan.getCommittedMinTemp(), loan.getCommittedMaxTemp(),
                    loan.getCommittedMinHumidity(), loan.getCommittedMaxHumidity());
            failures.addAll(artifactFailures.stream()
                    .map(f -> "藏品 " + artifact.getCatalogNo() + ": " + f).toList());
            boolean overlap = itemRepository.existsOverlappingApproved(
                    artifact.getId(), loan.getStartDate(), loan.getEndDate(), LoanStatus.APPROVED);
            if (overlap) {
                failures.add("藏品 " + artifact.getCatalogNo()
                        + " 在 " + loan.getStartDate() + "~" + loan.getEndDate()
                        + " 已存在日期重叠的已批准借展（闭区间，同日交接也算冲突）");
            }
        }
        if (!failures.isEmpty()) {
            throw new ConflictException("整组批准失败，全部藏品均未预留: " + String.join("; ", failures));
        }

        loan.approve(now);
        auditLogRepository.save(new LoanAuditLog(loan.getId(), "APPROVED", null, null, now));
        return new OperationOutcome(loan.getId(), 200, detail(loan));
    }

    // ------------------------------------------------------------------
    // 拒绝
    // ------------------------------------------------------------------

    public OperationOutcome reject(Long loanId, String reason) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        LoanRequest loan = getPendingForDecision(loanId);
        loan.reject(reason, now);
        auditLogRepository.save(new LoanAuditLog(loan.getId(), "REJECTED", reason, null, now));
        return new OperationOutcome(loan.getId(), 200, detail(loan));
    }

    // ------------------------------------------------------------------
    // 取消：只有尚未开始（开始日期晚于今天）的已批准借展可取消，取消即释放全部藏品
    // ------------------------------------------------------------------

    public OperationOutcome cancel(Long loanId, String reason) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        LocalDate today = LocalDate.now(clock);
        LoanRequest loan = loanRequestRepository.findByIdForUpdate(loanId)
                .orElseThrow(() -> new NotFoundException("借展申请不存在: " + loanId));
        if (loan.getStatus() != LoanStatus.APPROVED) {
            throw new ConflictException("仅已批准的借展可以取消，当前状态: " + loan.getStatus());
        }
        if (!loan.getStartDate().isAfter(today)) {
            throw new ConflictException("借展已开始或已过开始日期（" + loan.getStartDate()
                    + "），不能取消");
        }
        loan.cancel(reason, now);
        auditLogRepository.save(new LoanAuditLog(loan.getId(), "CANCELLED", reason, null, now));
        return new OperationOutcome(loan.getId(), 200, detail(loan));
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public LoanDetailResponse getDetail(Long loanId) {
        LoanRequest loan = loanRequestRepository.findById(loanId)
                .orElseThrow(() -> new NotFoundException("借展申请不存在: " + loanId));
        return detail(loan);
    }

    @Transactional(readOnly = true)
    public LoanCalendarResponse getCalendar(String catalogNo) {
        Artifact artifact = artifactRepository.findByCatalogNo(catalogNo)
                .orElseThrow(() -> new NotFoundException("藏品不存在: " + catalogNo));
        List<LoanCalendarEntry> entries = itemRepository.findApprovedByArtifactId(artifact.getId())
                .stream()
                .map(i -> {
                    LoanRequest r = i.getLoanRequest();
                    return new LoanCalendarEntry(r.getId(),
                            r.getInstitution().getCode(), r.getInstitution().getName(),
                            r.getStartDate(), r.getEndDate());
                })
                .toList();
        return new LoanCalendarResponse(artifact.getCatalogNo(), artifact.getName(), entries);
    }

    // ------------------------------------------------------------------
    // 内部校验与装配
    // ------------------------------------------------------------------

    private LoanRequest getPendingForDecision(Long loanId) {
        LoanRequest loan = loanRequestRepository.findByIdForUpdate(loanId)
                .orElseThrow(() -> new NotFoundException("借展申请不存在: " + loanId));
        if (loan.getStatus() != LoanStatus.PENDING) {
            throw new ConflictException("仅待审批申请可以执行该操作，当前状态: " + loan.getStatus());
        }
        return loan;
    }

    private void validateEligibility(List<Artifact> artifacts, Institution institution,
                                     com.chris64233.cc.museumloan.domain.RiskLevel transportRisk,
                                     BigDecimal minTemp, BigDecimal maxTemp,
                                     BigDecimal minHumidity, BigDecimal maxHumidity) {
        List<String> failures = new ArrayList<>();
        for (Artifact artifact : artifacts) {
            if (!artifact.isLoanable()) {
                failures.add("藏品 " + artifact.getCatalogNo() + " 当前不可外借");
                continue;
            }
            failures.addAll(eligibilityFailures(artifact, institution, transportRisk,
                    minTemp, maxTemp, minHumidity, maxHumidity).stream()
                    .map(f -> "藏品 " + artifact.getCatalogNo() + ": " + f).toList());
        }
        if (!failures.isEmpty()) {
            throw new ValidationException(String.join("; ", failures));
        }
    }

    private List<String> eligibilityFailures(Artifact artifact, Institution institution,
                                             com.chris64233.cc.museumloan.domain.RiskLevel transportRisk,
                                             BigDecimal committedMinTemp, BigDecimal committedMaxTemp,
                                             BigDecimal committedMinHumidity, BigDecimal committedMaxHumidity) {
        List<String> failures = new ArrayList<>();
        if (!institution.getMaxRisk().covers(transportRisk)) {
            failures.add("机构最高可承担风险 " + institution.getMaxRisk()
                    + " 不足以承担运输方案风险 " + transportRisk);
        }
        if (!institution.getMaxRisk().covers(artifact.getTransportRisk())) {
            failures.add("机构最高可承担风险 " + institution.getMaxRisk()
                    + " 不足以承担藏品运输风险 " + artifact.getTransportRisk());
        }
        if (committedMinTemp.compareTo(artifact.getMinTemp()) < 0
                || committedMaxTemp.compareTo(artifact.getMaxTemp()) > 0) {
            failures.add("承诺温度区间 [" + committedMinTemp + "," + committedMaxTemp
                    + "] 未完全落在藏品允许范围 [" + artifact.getMinTemp() + ","
                    + artifact.getMaxTemp() + "] 内");
        }
        if (committedMinHumidity.compareTo(artifact.getMinHumidity()) < 0
                || committedMaxHumidity.compareTo(artifact.getMaxHumidity()) > 0) {
            failures.add("承诺湿度区间 [" + committedMinHumidity + "," + committedMaxHumidity
                    + "] 未完全落在藏品允许范围 [" + artifact.getMinHumidity() + ","
                    + artifact.getMaxHumidity() + "] 内");
        }
        return failures;
    }

    private void validateRange(BigDecimal min, BigDecimal max, String label) {
        if (min == null || max == null) {
            throw new ValidationException(label + "不能为空");
        }
        if (min.compareTo(max) > 0) {
            throw new ValidationException(label + "下限不能大于上限");
        }
    }

    private LoanDetailResponse detail(LoanRequest loan) {
        List<LoanRequestItem> items = itemRepository.findDetailedByLoanRequestId(loan.getId());
        List<ArtifactResponse> artifacts = items.stream()
                .map(i -> ArtifactResponse.from(i.getArtifact()))
                .toList();
        List<AuditLogResponse> logs = auditLogRepository
                .findByLoanRequestIdOrderByIdAsc(loan.getId()).stream()
                .map(l -> new AuditLogResponse(l.getId(), l.getAction(), l.getReason(),
                        l.getOperator(), l.getCreatedAt()))
                .toList();
        return new LoanDetailResponse(loan.getId(),
                InstitutionResponse.from(loan.getInstitution()),
                artifacts, loan.getStartDate(), loan.getEndDate(),
                loan.getCommittedMinTemp(), loan.getCommittedMaxTemp(),
                loan.getCommittedMinHumidity(), loan.getCommittedMaxHumidity(),
                loan.getTransportRisk(), loan.getStatus(), loan.getReason(),
                loan.getCreatedAt(), loan.getDecidedAt(), logs);
    }

    /** 生成申请内容的规范化哈希，供幂等内容冲突判定。 */
    public static String applyContentHash(ApplyLoanRequest req) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("institutionCode", req.institutionCode());
        canonical.put("artifactCatalogNos", req.artifactCatalogNos().stream().sorted().toList());
        canonical.put("startDate", req.startDate().toString());
        canonical.put("endDate", req.endDate().toString());
        canonical.put("committedMinTemp", req.committedMinTemp().stripTrailingZeros().toPlainString());
        canonical.put("committedMaxTemp", req.committedMaxTemp().stripTrailingZeros().toPlainString());
        canonical.put("committedMinHumidity", req.committedMinHumidity().stripTrailingZeros().toPlainString());
        canonical.put("committedMaxHumidity", req.committedMaxHumidity().stripTrailingZeros().toPlainString());
        canonical.put("transportRisk", req.transportRisk().name());
        return JsonUtil.sha256(JsonUtil.toJson(canonical));
    }

    /** 批准操作的内容哈希（幂等键只能用于同一申请）。 */
    public static String approveContentHash(Long loanId) {
        return JsonUtil.sha256("loan:" + loanId);
    }

    /** 拒绝/取消操作的内容哈希（绑定申请与原因文本）。 */
    public static String reasonContentHash(Long loanId, String reason) {
        return JsonUtil.sha256("loan:" + loanId + "|reason:" + reason);
    }
}
