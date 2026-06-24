package com.typenull.pingdom.moderation.domain.audit;

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
        name = "admin_audit_log",
        indexes = {
                @Index(name = "idx_admin_audit_log_created", columnList = "created_at, id"),
                @Index(name = "idx_admin_audit_log_actor_created", columnList = "actor_user_id, created_at"),
                @Index(name = "idx_admin_audit_log_action_created", columnList = "action, created_at"),
                @Index(name = "idx_admin_audit_log_target_created", columnList = "target_type, target_id, created_at"),
                @Index(name = "idx_admin_audit_log_request_id", columnList = "request_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_username", length = 50)
    private String actorUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AdminAuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 50)
    private AdminAuditTargetType targetType;

    @Column(name = "target_id", nullable = false, length = 100)
    private String targetId;

    @Column(length = 500)
    private String reason;

    @Column(name = "before_state", columnDefinition = "TEXT")
    private String beforeState;

    @Column(name = "after_state", columnDefinition = "TEXT")
    private String afterState;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
