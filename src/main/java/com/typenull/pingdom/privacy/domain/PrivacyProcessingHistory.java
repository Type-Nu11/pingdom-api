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
public class PrivacyProcessingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subject_user_id")
    private Long subjectUserId;

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
