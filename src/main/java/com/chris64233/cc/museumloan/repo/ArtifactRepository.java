package com.chris64233.cc.museumloan.repo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.cc.museumloan.domain.Artifact;

public interface ArtifactRepository extends JpaRepository<Artifact, Long> {

    Optional<Artifact> findByCatalogNo(String catalogNo);

    /**
     * 对一组藏品加行级写锁，并按 id 排序加锁以避免死锁。
     * 两个并发整组批准若包含任一相同藏品，将在此处串行化。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Artifact a where a.id in :ids order by a.id asc")
    List<Artifact> findByIdInForUpdate(@Param("ids") Collection<Long> ids);
}
