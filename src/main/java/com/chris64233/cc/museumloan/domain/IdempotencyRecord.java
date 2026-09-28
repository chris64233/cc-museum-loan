package com.chris64233.cc.museumloan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 状态操作幂等记录。相同幂等键 + 相同请求内容重放返回原结果；内容冲突返回 409。
 */
@Entity
@Table(name = "idempotency_record", uniqueConstraints = {
        @UniqueConstraint(name = "uk_idempotency_key", columnNames = "idempotency_key")
})
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 客户端提供的幂等键（全局唯一） */
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    /** APPLY / APPROVE / REJECT / CANCEL */
    @Column(nullable = false, length = 16)
    private String operation;

    /** 请求内容的规范化哈希 */
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "loan_request_id")
    private Long loanRequestId;

    /** 首次执行返回的 HTTP 状态码 */
    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @Lob
    @Column(name = "response_body", nullable = false)
    private String responseBody;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String idempotencyKey, String operation, String contentHash,
                             Long loanRequestId, int httpStatus, String responseBody) {
        this.idempotencyKey = idempotencyKey;
        this.operation = operation;
        this.contentHash = contentHash;
        this.loanRequestId = loanRequestId;
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getOperation() {
        return operation;
    }

    public String getContentHash() {
        return contentHash;
    }

    public Long getLoanRequestId() {
        return loanRequestId;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
