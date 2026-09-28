package com.chris64233.cc.museumloan.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.cc.museumloan.domain.IdempotentOperation;
import com.chris64233.cc.museumloan.domain.IdempotencyRecord;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByOperationAndIdempotencyKey(IdempotentOperation operation,
                                                                 String idempotencyKey);
}
