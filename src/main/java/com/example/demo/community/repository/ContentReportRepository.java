package com.example.demo.community.repository;

import com.example.demo.community.entity.ContentReport;
import com.example.demo.community.entity.ContentReportReason;
import com.example.demo.community.entity.ContentReportStatus;
import com.example.demo.community.entity.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ContentReportRepository extends JpaRepository<ContentReport, Long> {

    boolean existsByReporterUserIdAndTargetTypeAndTargetIdAndStatusIn(Long reporterUserId,
                                                                      ReportTargetType targetType,
                                                                      Long targetId,
                                                                      Collection<ContentReportStatus> statuses);

    long countByStatus(ContentReportStatus status);

    long countByStatusIn(Collection<ContentReportStatus> statuses);

    long countByTargetTypeAndTargetIdAndStatusIn(ReportTargetType targetType,
                                                 Long targetId,
                                                 Collection<ContentReportStatus> statuses);

    @Query(value = """
        SELECT r FROM ContentReport r
        WHERE (:reporterUserId IS NULL OR r.reporterUserId = :reporterUserId)
          AND (:targetType IS NULL OR r.targetType = :targetType)
          AND (:status IS NULL OR r.status = :status)
          AND (:reasonCode IS NULL OR r.reasonCode = :reasonCode)
          AND (:createdFrom IS NULL OR r.createdAt >= :createdFrom)
          AND (:createdTo IS NULL OR r.createdAt < :createdTo)
        """,
        countQuery = """
        SELECT COUNT(r) FROM ContentReport r
        WHERE (:reporterUserId IS NULL OR r.reporterUserId = :reporterUserId)
          AND (:targetType IS NULL OR r.targetType = :targetType)
          AND (:status IS NULL OR r.status = :status)
          AND (:reasonCode IS NULL OR r.reasonCode = :reasonCode)
          AND (:createdFrom IS NULL OR r.createdAt >= :createdFrom)
          AND (:createdTo IS NULL OR r.createdAt < :createdTo)
        """)
    Page<ContentReport> searchForAdmin(@Param("reporterUserId") Long reporterUserId,
                                       @Param("targetType") ReportTargetType targetType,
                                       @Param("status") ContentReportStatus status,
                                       @Param("reasonCode") ContentReportReason reasonCode,
                                       @Param("createdFrom") LocalDateTime createdFrom,
                                       @Param("createdTo") LocalDateTime createdTo,
                                       Pageable pageable);

    List<ContentReport> findTop5ByOrderByCreatedAtDesc();

    @Modifying
    void deleteByTargetTypeAndTargetId(ReportTargetType targetType, Long targetId);

    @Modifying
    void deleteByTargetTypeAndTargetIdIn(ReportTargetType targetType, Collection<Long> targetIds);
}
