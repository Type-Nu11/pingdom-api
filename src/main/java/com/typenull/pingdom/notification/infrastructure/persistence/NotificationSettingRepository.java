package com.typenull.pingdom.notification.infrastructure.persistence;

import com.typenull.pingdom.notification.domain.NotificationSetting;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    Optional<NotificationSetting> findByUserId(Long userId);

    @Modifying
    @Query("""
            DELETE FROM NotificationSetting setting
            WHERE setting.userId = :userId
            """)
    int deleteAllByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("""
            DELETE FROM NotificationSetting setting
            WHERE setting.userId IN :userIds
            """)
    int deleteAllByUserIds(@Param("userIds") Collection<Long> userIds);
}
