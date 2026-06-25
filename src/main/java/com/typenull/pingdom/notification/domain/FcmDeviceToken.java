package com.typenull.pingdom.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "fcm_device_token",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_fcm_device_token_token", columnNames = "token")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FcmDeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 1000)
    private String token;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_registered_at", nullable = false)
    private LocalDateTime lastRegisteredAt;

    public static FcmDeviceToken create(Long userId, String token, LocalDateTime now) {
        FcmDeviceToken deviceToken = new FcmDeviceToken();
        deviceToken.userId = userId;
        deviceToken.token = token;
        deviceToken.createdAt = now;
        deviceToken.updatedAt = now;
        deviceToken.lastRegisteredAt = now;
        return deviceToken;
    }

    public void refresh(Long userId, LocalDateTime now) {
        this.userId = userId;
        this.updatedAt = now;
        this.lastRegisteredAt = now;
    }
}
