package com.example.demo.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<UserNotification, Long> {
    List<UserNotification> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long before, Pageable pageable);
    long countByUserIdAndReadAtIsNull(Long userId);
    boolean existsByIdAndUserId(Long id, Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UserNotification n set n.readAt = :now where n.id = :id and n.userId = :userId and n.readAt is null")
    int markRead(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UserNotification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
