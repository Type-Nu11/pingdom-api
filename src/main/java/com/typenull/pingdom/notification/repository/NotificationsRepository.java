package com.typenull.pingdom.notification.repository;

import com.typenull.pingdom.notification.domain.Notifications;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface NotificationsRepository extends JpaRepository<Notifications, Long> {
    Optional<Notifications> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("""
            DELETE FROM Notifications n
            WHERE n.userId = :userId
            """)
    int deleteAllByUserId(@Param("userId") Long userId);
}
