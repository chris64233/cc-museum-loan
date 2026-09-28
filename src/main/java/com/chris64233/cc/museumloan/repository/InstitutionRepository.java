package com.chris64233.cc.museumloan.repository;

import com.chris64233.cc.museumloan.domain.Institution;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstitutionRepository extends JpaRepository<Institution, Long> {

    Optional<Institution> findByCode(String code);
}
