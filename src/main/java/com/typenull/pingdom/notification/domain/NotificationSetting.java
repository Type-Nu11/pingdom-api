package com.typenull.pingdom.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "notification_settings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_notification_settings_user", columnNames = "user_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting {

    public static final String DEFAULT_TIMEZONE = "Asia/Seoul";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "new_hotplace_enabled", nullable = false)
    private boolean newHotplaceEnabled;

    @Column(name = "new_like_enabled", nullable = false)
    private boolean newLikeEnabled;

    @Column(name = "quiet_hours_enabled", nullable = false)
    private boolean quietHoursEnabled;

    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;

    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;

    @Column(nullable = false, length = 50)
    private String timezone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static NotificationSetting createDefault(Long userId, LocalDateTime now) {
        NotificationSetting setting = new NotificationSetting();
        setting.userId = userId;
        setting.newHotplaceEnabled = true;
        setting.newLikeEnabled = true;
        setting.quietHoursEnabled = false;
        setting.timezone = DEFAULT_TIMEZONE;
        setting.createdAt = now;
        setting.updatedAt = now;
        return setting;
    }

    public boolean isEnabled(NotificationType type) {
        return switch (type) {
            case NEW_HOTPLACE -> newHotplaceEnabled;
            case NEW_LIKE -> newLikeEnabled;
        };
    }

    public void updateNewHotplaceEnabled(boolean enabled, LocalDateTime now) {
        this.newHotplaceEnabled = enabled;
        touch(now);
    }

    public void updateNewLikeEnabled(boolean enabled, LocalDateTime now) {
        this.newLikeEnabled = enabled;
        touch(now);
    }

    public void updateQuietHoursEnabled(boolean enabled, LocalDateTime now) {
        this.quietHoursEnabled = enabled;
        touch(now);
    }

    public void updateQuietHours(LocalTime start, LocalTime end, LocalDateTime now) {
        this.quietHoursStart = start;
        this.quietHoursEnd = end;
        touch(now);
    }

    public void updateTimezone(String timezone, LocalDateTime now) {
        this.timezone = timezone;
        touch(now);
    }

    private void touch(LocalDateTime now) {
        this.updatedAt = now;
    }
}
