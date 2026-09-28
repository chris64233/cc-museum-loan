package com.chris64233.cc.museumloan.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.cc.museumloan.domain.IdempotentOperation;
import com.chris64233.cc.museumloan.domain.IdempotencyRecord;
import com.chris64233.cc.museumloan.repo.IdempotencyRecordRepository;
import com.chris64233.cc.museumloan.web.dto.LoanRequestResponse;
import com.chris64233.cc.museumloan.web.error.ConflictException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 状态操作幂等执行器。
 *
 * <p>语义：
 * <ul>
 *     <li>同一操作类型 + 同一幂等键 + 相同请求内容：重放首次成功结果，不重复执行；</li>
 *     <li>同一幂等键但请求内容不同：抛出 409 冲突；</li>
 *     <li>幂等记录与业务变更在同一事务提交，保证“操作”与“记录”同生共死；</li>
 *     <li>并发同键请求依赖数据库唯一约束兜底：后到者整事务回滚并提示重试，
 *         重试时即可读到首次记录并正常重放。</li>
 * </ul>
 *
 * <p>仅记录成功的操作结果；失败的操作未改变任何状态，重放会确定性地再次失败。
 */
@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRecordRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** 幂等执行结果。replayed=true 表示本次是重放，未真正执行业务动作。 */
    public record Outcome(LoanRequestResponse result, boolean replayed) {
    }

    /** 业务动作的执行结果：响应对象 + 关联申请编号。 */
    public record Execution(LoanRequestResponse result, String requestNo) {
    }

    /**
     * 在事务内执行带幂等保护的操作。
     *
     * @param operation   操作类型
     * @param key         客户端幂等键（非空）
     * @param contentHash 请求内容规范化哈希（见 {@link #sha256(String)}）
     * @param action      实际业务动作
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Outcome execute(IdempotentOperation operation, String key, String contentHash,
                           Supplier<Execution> action) {
        var existing = repository.findByOperationAndIdempotencyKey(operation, key);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (!record.getRequestHash().equals(contentHash)) {
                throw new ConflictException("幂等键已被不同内容的请求使用",
                        List.of("operation=" + operation, "idempotencyKey=" + key));
            }
            return new Outcome(deserialize(record.getResponseBody()), true);
        }

        Execution execution = action.get();
        try {
            repository.saveAndFlush(new IdempotencyRecord(operation, key, contentHash,
                    200, serialize(execution.result()), execution.requestNo(), Instant.now()));
        } catch (DataIntegrityViolationException duplicate) {
            throw new ConflictException("相同幂等键的请求正在并发处理，请稍后重试");
        }
        return new Outcome(execution.result(), false);
    }

    private String serialize(LoanRequestResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JacksonException e) {
            throw new IllegalStateException("响应序列化失败", e);
        }
    }

    private LoanRequestResponse deserialize(String body) {
        try {
            return objectMapper.readValue(body, LoanRequestResponse.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("幂等记录反序列化失败", e);
        }
    }

    /** 计算请求内容的 SHA-256 哈希（十六进制），用于幂等键的内容一致性判定。 */
    public static String sha256(String canonicalContent) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonicalContent.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
