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
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Getter
@Table(
        name = "user_travel_schedule",
        indexes = @Index(name = "idx_user_travel_schedule_user_period", columnList = "user_id, start_date, end_date")
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_travel_schedule_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_travel_schedule_user")
    )
    private User user;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TravelScheduleState state;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    private TravelSchedule(User user, LocalDate startDate, LocalDate endDate) {
        this.user = Objects.requireNonNull(user, "user must not be null");
        validatePeriod(startDate, endDate);
        this.startDate = startDate;
        this.endDate = endDate;
        this.state = TravelScheduleState.SCHEDULED;
    }

    public static TravelSchedule create(User user, LocalDate startDate, LocalDate endDate) {
        return new TravelSchedule(user, startDate, endDate);
    }

    public void updatePeriod(LocalDate startDate, LocalDate endDate) {
        ensureScheduled();
        validatePeriod(startDate, endDate);
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void cancel() {
        if (state == TravelScheduleState.CANCELLED) {
            return;
        }
        state = TravelScheduleState.CANCELLED;
    }

    public TravelScheduleStatus statusAt(LocalDate referenceDate) {
        LocalDate date = Objects.requireNonNull(referenceDate, "referenceDate must not be null");
        if (state == TravelScheduleState.CANCELLED) {
            return TravelScheduleStatus.CANCELLED;
        }
        if (date.isBefore(startDate)) {
            return TravelScheduleStatus.UPCOMING;
        }
        if (date.isAfter(endDate)) {
            return TravelScheduleStatus.ENDED;
        }
        return TravelScheduleStatus.ONGOING;
    }

    private void ensureScheduled() {
        if (state != TravelScheduleState.SCHEDULED) {
            throw new IllegalStateException("취소된 여행 일정은 수정할 수 없습니다.");
        }
    }

    private static void validatePeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("종료일은 시작일보다 빠를 수 없습니다.");
        }
    }
}
