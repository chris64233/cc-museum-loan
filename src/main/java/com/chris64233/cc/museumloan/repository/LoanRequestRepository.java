package com.chris64233.cc.museumloan.repository;

import com.chris64233.cc.museumloan.domain.LoanRequest;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanRequestRepository extends JpaRepository<LoanRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LoanRequest r where r.id = :id")
    Optional<LoanRequest> findByIdForUpdate(@Param("id") Long id);
}
