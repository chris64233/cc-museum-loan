package com.chris64233.cc.museumloan.repository;

import com.chris64233.cc.museumloan.domain.Artifact;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArtifactRepository extends JpaRepository<Artifact, Long> {

    Optional<Artifact> findByCatalogNo(String catalogNo);

    List<Artifact> findByCatalogNoIn(List<String> catalogNos);

    /** 按 id 升序加行锁，避免多事务交叉加锁导致死锁。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Artifact a where a.id in :ids order by a.id asc")
    List<Artifact> findAllByIdForUpdate(@Param("ids") List<Long> ids);
}
