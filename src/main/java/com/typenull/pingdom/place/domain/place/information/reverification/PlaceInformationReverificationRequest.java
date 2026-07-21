package com.typenull.pingdom.place.domain.place.information.reverification;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationEvidence;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@Entity
@Getter
@Table(name = "place_information_reverification_request")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceInformationReverificationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_information_reverification_request_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "map_place_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_place_information_reverification_place"))
    private MapPlace place;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_information_evidence_id",
            foreignKey = @ForeignKey(name = "fk_place_information_reverification_evidence"))
    private PlaceInformationEvidence evidence;

    @Column(name = "merchant_owner_user_id", nullable = false)
    private Long merchantOwnerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PlaceInformationReverificationStatus status;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "requested_by_admin_user_id", nullable = false)
    private Long requestedByAdminUserId;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "due_at", nullable = false)
    private LocalDateTime dueAt;

    @Column(name = "last_reminded_at")
    private LocalDateTime lastRemindedAt;

    @Column(name = "reminder_count", nullable = false)
    private int reminderCount;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "response_note", length = 1000)
    private String responseNote;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public static PlaceInformationReverificationRequest create(
            MapPlace place, Long merchantOwnerUserId, String reason, Long adminUserId,
            LocalDateTime dueAt, LocalDateTime now
    ) {
        String normalizedReason = requireText(reason, "reason");
        Objects.requireNonNull(now, "now must not be null");
        if (dueAt == null || !dueAt.isAfter(now)) {
            throw new IllegalArgumentException("dueAt must be after requestedAt");
        }
        PlaceInformationReverificationRequest request = new PlaceInformationReverificationRequest();
        request.place = Objects.requireNonNull(place, "place must not be null");
        request.merchantOwnerUserId = Objects.requireNonNull(merchantOwnerUserId, "merchantOwnerUserId must not be null");
        request.requestedByAdminUserId = Objects.requireNonNull(adminUserId, "adminUserId must not be null");
        request.reason = normalizedReason;
        request.status = PlaceInformationReverificationStatus.REQUESTED;
        request.requestedAt = now;
        request.dueAt = dueAt;
        request.updatedAt = now;
        return request;
    }

    public void respond(Long merchantUserId, String responseNote, PlaceInformationEvidence evidence, LocalDateTime now) {
        requireStatus(PlaceInformationReverificationStatus.REQUESTED);
        if (!now.isBefore(dueAt)) {
            throw new IllegalStateException("request has expired");
        }
        this.merchantOwnerUserId = Objects.requireNonNull(merchantUserId, "merchantUserId must not be null");
        this.responseNote = requireText(responseNote, "responseNote");
        this.evidence = Objects.requireNonNull(evidence, "evidence must not be null");
        status = PlaceInformationReverificationStatus.RESPONDED;
        respondedAt = now;
        updatedAt = now;
    }

    public void remind(LocalDateTime now) {
        requireStatus(PlaceInformationReverificationStatus.REQUESTED);
        if (!now.isBefore(dueAt)) {
            throw new IllegalStateException("request has expired");
        }
        reminderCount++;
        lastRemindedAt = now;
        updatedAt = now;
    }

    public boolean isDue(LocalDateTime now) {
        return !now.isBefore(dueAt);
    }

    public void reassignOwner(Long merchantOwnerUserId, LocalDateTime now) {
        requireStatus(PlaceInformationReverificationStatus.REQUESTED);
        this.merchantOwnerUserId = Objects.requireNonNull(merchantOwnerUserId, "merchantOwnerUserId must not be null");
        updatedAt = now;
    }

    public void complete(LocalDateTime now) {
        requireStatus(PlaceInformationReverificationStatus.RESPONDED);
        status = PlaceInformationReverificationStatus.COMPLETED;
        completedAt = now;
        updatedAt = now;
    }

    public void cancel(LocalDateTime now) {
        if (status != PlaceInformationReverificationStatus.REQUESTED
                && status != PlaceInformationReverificationStatus.RESPONDED) {
            throw new IllegalStateException("only active request can be canceled");
        }
        status = PlaceInformationReverificationStatus.CANCELED;
        canceledAt = now;
        updatedAt = now;
    }

    public void expire(LocalDateTime now) {
        requireStatus(PlaceInformationReverificationStatus.REQUESTED);
        if (now.isBefore(dueAt)) {
            throw new IllegalStateException("request is not due");
        }
        status = PlaceInformationReverificationStatus.EXPIRED;
        updatedAt = now;
    }

    private void requireStatus(PlaceInformationReverificationStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("invalid status transition: " + status + " -> " + expected);
        }
    }

    private static String requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
