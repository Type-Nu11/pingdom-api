package com.typenull.pingdom.identity.domain.travel;

import com.typenull.pingdom.identity.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 회원당 하나의 단기 탐색 의도와 만료 시각을 보관.
 * 기준 시각이 만료 시각보다 엄격히 이전일 때만 활성으로 판정하며 TTL 길이는 서비스가 결정.
 */
@Entity
@Getter
@Table(
        name = "user_current_activity_intent",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_current_activity_intent_user",
                columnNames = "user_id"
        ),
        indexes = @Index(name = "idx_user_current_activity_intent_expires_at", columnList = "expires_at")
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCurrentActivityIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_current_activity_intent_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_current_activity_intent_user")
    )
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_intent", nullable = false, length = 30)
    private CurrentActivityIntent intent;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private UserCurrentActivityIntent(
            User user,
            CurrentActivityIntent intent,
            LocalDateTime expiresAt
    ) {
        this.user = Objects.requireNonNull(user, "user must not be null");
        replace(intent, expiresAt);
    }

    public static UserCurrentActivityIntent create(
            User user,
            CurrentActivityIntent intent,
            LocalDateTime expiresAt
    ) {
        return new UserCurrentActivityIntent(user, intent, expiresAt);
    }

    public void replace(CurrentActivityIntent intent, LocalDateTime expiresAt) {
        this.intent = Objects.requireNonNull(intent, "intent must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public boolean isActiveAt(LocalDateTime referenceTime) {
        return expiresAt.isAfter(Objects.requireNonNull(referenceTime, "referenceTime must not be null"));
    }
}
