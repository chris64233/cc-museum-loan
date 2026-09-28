package com.chris64233.cc.museumloan.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.cc.museumloan.domain.BorrowingInstitution;

public interface BorrowingInstitutionRepository extends JpaRepository<BorrowingInstitution, Long> {

    Optional<BorrowingInstitution> findByCode(String code);
}
