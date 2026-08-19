package com.typenull.pingdom.notification.infrastructure.persistence;

import com.typenull.pingdom.notification.domain.Notifications;
import com.typenull.pingdom.notification.domain.NotificationType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationsRepository extends JpaRepository<Notifications, Long> {
    Optional<Notifications> findByIdAndUserId(Long id, Long userId);

    Optional<Notifications> findByIdAndUserIdAndTypeIn(
            Long id,
            Long userId,
            Collection<NotificationType> types
    );

    long countByUserIdAndTypeInAndIsReadFalse(Long userId, Collection<NotificationType> types);

    @Query("""
            SELECT notification
            FROM Notifications notification
            WHERE notification.userId = :userId
              AND notification.type IN :adminTypes
              AND (:type IS NULL OR notification.type = :type)
              AND (:read IS NULL OR notification.isRead = :read)
              AND (:hasFrom = false OR notification.createdAt >= :from)
              AND (:hasTo = false OR notification.createdAt <= :to)
            """)
    Page<Notifications> findByAdminFilters(
            @Param("userId") Long userId,
            @Param("adminTypes") Collection<NotificationType> adminTypes,
            @Param("type") NotificationType type,
            @Param("read") Boolean read,
            @Param("hasFrom") boolean hasFrom,
            @Param("from") LocalDateTime from,
            @Param("hasTo") boolean hasTo,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Notifications notification
            SET notification.isRead = true
            WHERE notification.userId = :userId
              AND notification.type IN :adminTypes
              AND notification.isRead = false
            """)
    int markAllAdminNotificationsAsRead(
            @Param("userId") Long userId,
            @Param("adminTypes") Collection<NotificationType> adminTypes
    );

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO notifications (
                token, event_key, type, user_id, title, body, is_read, created_at
            ) VALUES (
                :token, :eventKey, :type, :userId, :title, :body, false, :createdAt
            )
            ON CONFLICT (user_id, event_key) DO NOTHING
            """, nativeQuery = true)
    int insertAdminNotificationIfAbsent(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("title") String title,
            @Param("body") String body,
            @Param("token") String token,
            @Param("eventKey") String eventKey,
            @Param("createdAt") LocalDateTime createdAt
    );

    @Modifying
    @Query("""
            DELETE FROM Notifications n
            WHERE n.userId = :userId
            """)
    int deleteAllByUserId(@Param("userId") Long userId);
}
