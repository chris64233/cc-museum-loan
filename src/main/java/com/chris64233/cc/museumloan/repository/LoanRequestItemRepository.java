package com.chris64233.cc.museumloan.repository;

import com.chris64233.cc.museumloan.domain.LoanRequestItem;
import com.chris64233.cc.museumloan.domain.LoanStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanRequestItemRepository extends JpaRepository<LoanRequestItem, Long> {

    @Query("select i from LoanRequestItem i join fetch i.artifact a join fetch i.loanRequest r "
            + "where i.loanRequest.id = :loanRequestId order by i.id asc")
    List<LoanRequestItem> findDetailedByLoanRequestId(@Param("loanRequestId") Long loanRequestId);

    /**
     * 某藏品在给定闭区间 [startDate, endDate] 内是否存在已批准借展（同一天交接也算冲突）。
     */
    @Query("""
            select case when count(i) > 0 then true else false end
            from LoanRequestItem i
            where i.artifact.id = :artifactId
              and i.loanRequest.status = :status
              and i.loanRequest.startDate <= :endDate
              and i.loanRequest.endDate >= :startDate
            """)
    boolean existsOverlappingApproved(@Param("artifactId") Long artifactId,
                                      @Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate,
                                      @Param("status") LoanStatus status);

    /**
     * 某藏品的已批准借展明细，供日历查询。
     */
    @Query("""
            select i from LoanRequestItem i
            join fetch i.loanRequest r
            join fetch r.institution
            where i.artifact.id = :artifactId and r.status = com.chris64233.cc.museumloan.domain.LoanStatus.APPROVED
            order by r.startDate asc, r.id asc
            """)
    List<LoanRequestItem> findApprovedByArtifactId(@Param("artifactId") Long artifactId);
}
