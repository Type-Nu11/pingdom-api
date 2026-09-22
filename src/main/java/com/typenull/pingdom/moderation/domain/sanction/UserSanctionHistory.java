package com.typenull.pingdom.moderation.domain.sanction;

import com.typenull.pingdom.identity.domain.UserBanType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 제재 적용·해제·자동 만료 당시 사용자명과 기간을 보관하는 이력.
 * 자동 만료는 관리자 ID가 없고, endedAt은 실제 처리 시각과 별도로 기존 제재의 예정 종료 시각을 담을 수 있음.
 */
@Getter
@Entity
@Table(name = "user_sanction_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserSanctionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Column(name = "target_username", length = 50)
    private String targetUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "ban_type", length = 20)
    private UserBanType banType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserSanctionAction action;

    @Column(length = 255)
    private String reason;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "admin_user_id")
    private Long adminUserId;

    @Column(name = "admin_username", length = 50)
    private String adminUsername;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
}
