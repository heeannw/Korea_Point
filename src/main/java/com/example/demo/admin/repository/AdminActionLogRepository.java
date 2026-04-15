package com.example.demo.admin.repository;

import com.example.demo.admin.entity.AdminActionLog;
import com.example.demo.admin.entity.AdminActionType;
import com.example.demo.admin.entity.AdminReasonCode;
import com.example.demo.admin.entity.AdminTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AdminActionLogRepository extends JpaRepository<AdminActionLog, Long> {

    List<AdminActionLog> findTop5ByOrderByCreatedAtDesc();

    long countByActionTypeInAndCreatedAtGreaterThanEqual(Collection<AdminActionType> actionTypes,
                                                         LocalDateTime createdAt);

    @Query(value = """
        SELECT l FROM AdminActionLog l
        WHERE (:adminId IS NULL OR l.adminUserId = :adminId)
          AND (:targetType IS NULL OR l.targetType = :targetType)
          AND (:actionType IS NULL OR l.actionType = :actionType)
          AND (:reasonCode IS NULL OR l.reasonCode = :reasonCode)
          AND (:createdFrom IS NULL OR l.createdAt >= :createdFrom)
          AND (:createdTo IS NULL OR l.createdAt < :createdTo)
        """,
        countQuery = """
        SELECT COUNT(l) FROM AdminActionLog l
        WHERE (:adminId IS NULL OR l.adminUserId = :adminId)
          AND (:targetType IS NULL OR l.targetType = :targetType)
          AND (:actionType IS NULL OR l.actionType = :actionType)
          AND (:reasonCode IS NULL OR l.reasonCode = :reasonCode)
          AND (:createdFrom IS NULL OR l.createdAt >= :createdFrom)
          AND (:createdTo IS NULL OR l.createdAt < :createdTo)
        """)
    Page<AdminActionLog> searchForAdmin(@Param("adminId") Long adminId,
                                        @Param("targetType") AdminTargetType targetType,
                                        @Param("actionType") AdminActionType actionType,
                                        @Param("reasonCode") AdminReasonCode reasonCode,
                                        @Param("createdFrom") LocalDateTime createdFrom,
                                        @Param("createdTo") LocalDateTime createdTo,
                                        Pageable pageable);
}
