package com.chris64233.cc.museumloan.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 状态操作的幂等记录。
 *
 * <p>客户端对每次状态操作（提交/批准/拒绝/取消）携带请求幂等键。
 * 同一幂等键 + 同一操作类型的首次请求落库一条记录；相同内容重放直接返回首次结果，
 * 内容冲突（同键但请求体不同）返回 409。
 *
 * <p>{@code (operation, idempotency_key)} 上的唯一约束保证并发重放只有一个请求落库。
 */
@Entity
@Table(name = "idempotency_record", uniqueConstraints = {
        @UniqueConstraint(name = "uk_idempotency_op_key",
                columnNames = {"operation", "idempotency_key"})
})
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, updatable = false, length = 16)
    private IdempotentOperation operation;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    /** 首次请求内容的规范化哈希（SHA-256 十六进制），用于内容冲突判定。 */
    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    /** 首次成功响应的 HTTP 状态码。 */
    @Column(name = "response_status", nullable = false, updatable = false)
    private int responseStatus;

    /** 首次成功响应体（JSON）。 */
    @Lob
    @Column(name = "response_body", nullable = false, updatable = false)
    private String responseBody;

    /** 关联的申请编号（便于追溯，非外键约束以兼容响应回放）。 */
    @Column(name = "request_no", updatable = false, length = 64)
    private String requestNo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(IdempotentOperation operation, String idempotencyKey,
                             String requestHash, int responseStatus, String responseBody,
                             String requestNo, Instant createdAt) {
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.requestNo = requestNo;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public IdempotentOperation getOperation() {
        return operation;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getRequestNo() {
        return requestNo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
