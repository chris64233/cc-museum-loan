package com.chris64233.cc.museumloan.repo;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.cc.museumloan.domain.LoanRequestItem;
import com.chris64233.cc.museumloan.domain.LoanStatus;

public interface LoanRequestItemRepository extends JpaRepository<LoanRequestItem, Long> {

    /**
     * 闭区间日期重叠判定：existing.start &lt;= candidate.end 且 existing.end &gt;= candidate.start。
     * 同一天交接（existing.start == candidate.end 或反之）也算重叠冲突。
     * 仅“已批准”的借展构成占用；待审批申请不预留藏品。
     */
    @Query("""
            select i from LoanRequestItem i
            where i.artifact.id = :artifactId
              and i.loanRequest.status = :status
              and i.loanRequest.id <> :excludeRequestId
              and i.loanRequest.startDate <= :endDate
              and i.loanRequest.endDate >= :startDate
            """)
    List<LoanRequestItem> findOverlapping(@Param("artifactId") Long artifactId,
                                          @Param("status") LoanStatus status,
                                          @Param("excludeRequestId") Long excludeRequestId,
                                          @Param("startDate") LocalDate startDate,
                                          @Param("endDate") LocalDate endDate);

    @Query("""
            select i from LoanRequestItem i
            where i.artifact.id = :artifactId
              and i.loanRequest.status = :status
              and (:fromDate is null or i.loanRequest.endDate >= :fromDate)
              and (:toDate is null or i.loanRequest.startDate <= :toDate)
            order by i.loanRequest.startDate asc, i.loanRequest.endDate asc
            """)
    List<LoanRequestItem> findCalendar(@Param("artifactId") Long artifactId,
                                       @Param("status") LoanStatus status,
                                       @Param("fromDate") LocalDate fromDate,
                                       @Param("toDate") LocalDate toDate);
}
