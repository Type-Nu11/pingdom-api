package com.typenull.pingdom.moderation.domain.recommendation;

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
        name = "admin_recommendation_policy_change_history",
        indexes = {
                @Index(
                        name = "idx_admin_recommendation_policy_change_history_created",
                        columnList = "changed_at DESC, id DESC"
                ),
                @Index(
                        name = "idx_admin_recommendation_policy_change_history_version",
                        columnList = "recommendation_version, changed_at DESC"
                ),
                @Index(
                        name = "idx_admin_recommendation_policy_change_history_actor",
                        columnList = "actor_user_id, changed_at DESC"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AdminRecommendationPolicyChangeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recommendation_version", nullable = false, length = 100)
    private String recommendationVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 50)
    private AdminRecommendationPolicyChangeType changeType;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(length = 500)
    private String reason;

    @Column(name = "before_state", nullable = false, columnDefinition = "TEXT")
    private String beforeState;

    @Column(name = "after_state", nullable = false, columnDefinition = "TEXT")
    private String afterState;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;
}
