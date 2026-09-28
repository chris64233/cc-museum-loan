package com.chris64233.cc.museumloan.repository;

import com.chris64233.cc.museumloan.domain.LoanAuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanAuditLogRepository extends JpaRepository<LoanAuditLog, Long> {

    List<LoanAuditLog> findByLoanRequestIdOrderByIdAsc(Long loanRequestId);
}
