package com.typenull.pingdom.notification.repository;

import com.typenull.pingdom.notification.domain.Notifications;
import com.typenull.pingdom.notification.domain.NotificationType;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface NotificationsRepository extends JpaRepository<Notifications, Long> {
    Optional<Notifications> findByIdAndUserId(Long id, Long userId);

    long countByIsReadFalse();

    @Query("""
            SELECT notification
            FROM Notifications notification
            WHERE (:userId IS NULL OR notification.userId = :userId)
              AND (:type IS NULL OR notification.type = :type)
              AND (:read IS NULL OR notification.isRead = :read)
              AND (:from IS NULL OR notification.createdAt >= :from)
              AND (:to IS NULL OR notification.createdAt <= :to)
            """)
    Page<Notifications> findByAdminFilters(
            @Param("userId") Long userId,
            @Param("type") NotificationType type,
            @Param("read") Boolean read,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    @Modifying
    @Query("""
            UPDATE Notifications notification
            SET notification.isRead = true
            WHERE notification.isRead = false
            """)
    int markAllAsRead();

    @Modifying
    @Query("""
            DELETE FROM Notifications n
            WHERE n.userId = :userId
            """)
    int deleteAllByUserId(@Param("userId") Long userId);
}
