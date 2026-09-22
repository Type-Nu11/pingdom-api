package com.typenull.pingdom.identity.domain.merchant;

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
 * 장소 팀원 초대의 대상·역할·만료·응답 상태를 보관.
 * 초대는 OWNER를 허용하지 않고 수락·회수는 PENDING 상태에서만 가능.
 */
@Getter
@Entity
@Table(name = "merchant_place_invitation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MerchantPlaceInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "invitee_user_id", nullable = false)
    private Long inviteeUserId;

    @Column(name = "invited_by", nullable = false)
    private Long invitedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MerchantPlaceMemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MerchantPlaceInvitationStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static MerchantPlaceInvitation pending(
            Long placeId, Long inviteeUserId, Long invitedBy, MerchantPlaceMemberRole role,
            LocalDateTime expiresAt, LocalDateTime now) {
        if (role == MerchantPlaceMemberRole.OWNER) {
            throw new IllegalArgumentException("초대에는 OWNER 권한을 사용할 수 없습니다.");
        }
        return MerchantPlaceInvitation.builder()
                .placeId(placeId)
                .inviteeUserId(inviteeUserId)
                .invitedBy(invitedBy)
                .role(role)
                .status(MerchantPlaceInvitationStatus.PENDING)
                .expiresAt(expiresAt)
                .createdAt(now)
                .build();
    }

    /**
     * 만료 시각 이상이면 메모리 상태를 EXPIRED로 바꾼 후 예외 발생.
     * 호출 트랜잭션이 예외로 롤백되면 이 상태 변경도 저장되지 않으므로 만료 표시의 영속화는 보장 범위에서 제외.
     */
    public void accept(LocalDateTime now) {
        requirePending();
        if (!now.isBefore(expiresAt)) {
            status = MerchantPlaceInvitationStatus.EXPIRED;
            throw new IllegalStateException("만료된 팀원 초대입니다.");
        }
        status = MerchantPlaceInvitationStatus.ACCEPTED;
        respondedAt = now;
    }

    public void revoke(LocalDateTime now) {
        requirePending();
        status = MerchantPlaceInvitationStatus.REVOKED;
        respondedAt = now;
    }

    private void requirePending() {
        if (status != MerchantPlaceInvitationStatus.PENDING) {
            throw new IllegalStateException("대기 중인 팀원 초대만 처리할 수 있습니다.");
        }
    }
}
