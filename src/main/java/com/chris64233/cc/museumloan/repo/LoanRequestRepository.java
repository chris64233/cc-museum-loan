package com.chris64233.cc.museumloan.repo;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.cc.museumloan.domain.LoanRequest;

public interface LoanRequestRepository extends JpaRepository<LoanRequest, Long> {

    Optional<LoanRequest> findByRequestNo(String requestNo);

    /** 对申请行加写锁，串行化同一申请上的并发状态操作。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LoanRequest r where r.requestNo = :requestNo")
    Optional<LoanRequest> findByRequestNoForUpdate(@Param("requestNo") String requestNo);
}
