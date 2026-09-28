package com.chris64233.cc.museumloan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 借展申请中的单件藏品行。
 *
 * <p>{@code (loan_request_id, artifact_id)} 唯一约束在数据库层再次保证
 * 同一申请内藏品不重复；{@code (artifact_id, loan_request_id)} 建有索引以加速日历/冲突查询。
 */
@Entity
@Table(name = "loan_request_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_request_artifact",
                columnNames = {"loan_request_id", "artifact_id"})
}, indexes = {
        @jakarta.persistence.Index(name = "idx_item_artifact", columnList = "artifact_id,loan_request_id")
})
public class LoanRequestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_request_id", nullable = false, updatable = false)
    private LoanRequest loanRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artifact_id", nullable = false, updatable = false)
    private Artifact artifact;

    /** 提交时快照的藏品号，防止藏品主数据后续变动影响历史申请可读性。 */
    @Column(name = "catalog_no", nullable = false, updatable = false, length = 64)
    private String catalogNo;

    protected LoanRequestItem() {
    }

    public LoanRequestItem(LoanRequest loanRequest, Artifact artifact) {
        this.loanRequest = loanRequest;
        this.artifact = artifact;
        this.catalogNo = artifact.getCatalogNo();
    }

    public Long getId() {
        return id;
    }

    public LoanRequest getLoanRequest() {
        return loanRequest;
    }

    public Artifact getArtifact() {
        return artifact;
    }

    public String getCatalogNo() {
        return catalogNo;
    }
}
