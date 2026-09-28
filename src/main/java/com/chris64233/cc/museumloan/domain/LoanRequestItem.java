package com.chris64233.cc.museumloan.domain;

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
 * 申请内的单件藏品行。同一申请内藏品唯一。
 */
@Entity
@Table(name = "loan_request_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_loan_item_request_artifact",
                columnNames = {"loan_request_id", "artifact_id"})
})
public class LoanRequestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_request_id", nullable = false)
    private LoanRequest loanRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artifact_id", nullable = false)
    private Artifact artifact;

    protected LoanRequestItem() {
    }

    public LoanRequestItem(LoanRequest loanRequest, Artifact artifact) {
        this.loanRequest = loanRequest;
        this.artifact = artifact;
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
}
