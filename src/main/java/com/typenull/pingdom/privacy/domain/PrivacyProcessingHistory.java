package com.typenull.pingdom.privacy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "privacy_processing_history",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_privacy_processing_history_outbox_subject",
                        columnNames = {"outbox_event_id", "subject_user_id"}
                )
        },
        indexes = {
                @Index(name = "idx_privacy_processing_history_created", columnList = "created_at DESC, id DESC"),
                @Index(name = "idx_privacy_processing_history_subject_created", columnList = "subject_user_id, created_at DESC"),
                @Index(name = "idx_privacy_processing_history_action_created", columnList = "action, created_at DESC"),
                @Index(name = "idx_privacy_processing_history_actor_created", columnList = "actor_user_id, created_at DESC")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
/** 개인정보 처리 주체·행위·대상과 처리 시점을 감사 이력으로 보존합니다. */
public class PrivacyProcessingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subject_user_id")
    private Long subjectUserId;

    /** Outbox 재처리 시 동일 감사 이력의 중복 생성을 막는 원본 이벤트 식별자입니다. */
    @Column(name = "outbox_event_id", length = 36, updatable = false)
    private String outboxEventId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20)
    private PrivacyProcessingActorType actorType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PrivacyProcessingAction action;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
