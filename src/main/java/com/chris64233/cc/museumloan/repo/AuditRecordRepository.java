package com.chris64233.cc.museumloan.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.cc.museumloan.domain.AuditRecord;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long> {

    List<AuditRecord> findByLoanRequestIdOrderByOccurredAtAscIdAsc(Long loanRequestId);
}
