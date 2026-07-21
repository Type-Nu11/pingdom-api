package com.typenull.pingdom.place.domain.place.operating.notice;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@Entity
@Getter
@Table(name = "place_operating_notice")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceOperatingNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_operating_notice_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "map_place_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_place_operating_notice_place")
    )
    private MapPlace place;

    @Enumerated(EnumType.STRING)
    @Column(name = "notice_type", nullable = false, length = 30)
    private PlaceOperatingNoticeType noticeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private PlaceOperatingNoticeSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PlaceOperatingNoticeStatus status;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    private PlaceOperatingNotice(
            MapPlace place,
            PlaceOperatingNoticeType noticeType,
            PlaceOperatingNoticeSeverity severity,
            String message,
            LocalDateTime startsAt,
            LocalDateTime expiresAt,
            Long createdByUserId,
            LocalDateTime createdAt
    ) {
        this.place = Objects.requireNonNull(place, "place must not be null");
        this.noticeType = Objects.requireNonNull(noticeType, "noticeType must not be null");
        this.severity = Objects.requireNonNull(severity, "severity must not be null");
        this.message = requireText(message, "message must not be blank");
        this.startsAt = Objects.requireNonNull(startsAt, "startsAt must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!this.startsAt.isBefore(this.expiresAt)) {
            throw new IllegalArgumentException("startsAt must be before expiresAt");
        }
        this.createdByUserId = Objects.requireNonNull(createdByUserId, "createdByUserId must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = createdAt;
        this.status = startsAt.isAfter(createdAt)
                ? PlaceOperatingNoticeStatus.SCHEDULED
                : PlaceOperatingNoticeStatus.ACTIVE;
    }

    public static PlaceOperatingNotice create(
            MapPlace place,
            PlaceOperatingNoticeType noticeType,
            PlaceOperatingNoticeSeverity severity,
            String message,
            LocalDateTime startsAt,
            LocalDateTime expiresAt,
            Long createdByUserId,
            LocalDateTime createdAt
    ) {
        return new PlaceOperatingNotice(
                place,
                noticeType,
                severity,
                message,
                startsAt,
                expiresAt,
                createdByUserId,
                createdAt
        );
    }

    public boolean isVisibleAt(LocalDateTime now) {
        LocalDateTime checkedAt = Objects.requireNonNull(now, "now must not be null");
        return status == PlaceOperatingNoticeStatus.ACTIVE
                && !checkedAt.isBefore(startsAt)
                && checkedAt.isBefore(expiresAt);
    }

    public boolean shouldExpire(LocalDateTime now) {
        LocalDateTime checkedAt = Objects.requireNonNull(now, "now must not be null");
        return !status.isTerminal() && !checkedAt.isBefore(expiresAt);
    }

    public void activate(LocalDateTime activatedAt) {
        ensureNotTerminal();
        LocalDateTime changedAt = Objects.requireNonNull(activatedAt, "activatedAt must not be null");
        if (changedAt.isBefore(startsAt)) {
            throw new IllegalStateException("scheduled notice cannot be activated before startsAt");
        }
        if (!changedAt.isBefore(expiresAt)) {
            throw new IllegalStateException("expired notice cannot be activated");
        }
        this.status = PlaceOperatingNoticeStatus.ACTIVE;
        this.updatedAt = changedAt;
    }

    public void updateContent(
            PlaceOperatingNoticeSeverity severity,
            String message,
            Long updatedByUserId,
            LocalDateTime updatedAt
    ) {
        ensureNotTerminal();
        this.severity = Objects.requireNonNull(severity, "severity must not be null");
        this.message = requireText(message, "message must not be blank");
        this.updatedByUserId = Objects.requireNonNull(updatedByUserId, "updatedByUserId must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public void expire(LocalDateTime expiredAt) {
        if (status.isTerminal()) {
            return;
        }
        LocalDateTime changedAt = Objects.requireNonNull(expiredAt, "expiredAt must not be null");
        if (changedAt.isBefore(expiresAt)) {
            throw new IllegalStateException("notice cannot expire before expiresAt");
        }
        this.status = PlaceOperatingNoticeStatus.EXPIRED;
        this.expiredAt = changedAt;
        this.updatedAt = changedAt;
    }

    public void cancel(Long canceledByUserId, String cancelReason, LocalDateTime canceledAt) {
        ensureNotTerminal();
        Long actorUserId = Objects.requireNonNull(canceledByUserId, "canceledByUserId must not be null");
        String normalizedReason = requireText(cancelReason, "cancelReason must not be blank");
        LocalDateTime changedAt = Objects.requireNonNull(canceledAt, "canceledAt must not be null");
        this.status = PlaceOperatingNoticeStatus.CANCELED;
        this.updatedByUserId = actorUserId;
        this.cancelReason = normalizedReason;
        this.canceledAt = changedAt;
        this.updatedAt = changedAt;
    }

    private void ensureNotTerminal() {
        if (status.isTerminal()) {
            throw new IllegalStateException("terminal notice cannot be changed");
        }
    }

    private static String requireText(String value, String message) {
        String normalized = StringUtils.hasText(value) ? value.trim() : null;
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
