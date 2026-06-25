package com.typenull.pingdom.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "password_reset_token",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_password_reset_token_hash", columnNames = "token_hash")
        },
        indexes = {
                @Index(name = "idx_password_reset_token_user_active", columnList = "user_id, used_at, expires_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_password_reset_token_user")
    )
    private User user;

    @Column(name = "token_hash", length = 64, nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static PasswordResetToken create(
            User user,
            String tokenHash,
            LocalDateTime expiresAt,
            LocalDateTime now
    ) {
        PasswordResetToken token = new PasswordResetToken();
        token.user = user;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        token.createdAt = now;
        return token;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public void markUsed(LocalDateTime now) {
        if (usedAt == null) {
            usedAt = now;
        }
    }
}
