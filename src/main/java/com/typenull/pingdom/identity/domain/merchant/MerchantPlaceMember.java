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

@Getter
@Entity
@Table(name = "merchant_place_member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MerchantPlaceMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MerchantPlaceMemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MerchantPlaceMemberStatus status;

    @Column(name = "invited_by", nullable = false)
    private Long invitedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static MerchantPlaceMember owner(Long placeId, Long userId, LocalDateTime now) {
        return create(placeId, userId, MerchantPlaceMemberRole.OWNER, userId, now);
    }

    public static MerchantPlaceMember create(
            Long placeId, Long userId, MerchantPlaceMemberRole role, Long invitedBy, LocalDateTime now) {
        if (role == MerchantPlaceMemberRole.OWNER && !userId.equals(invitedBy)) {
            throw new IllegalArgumentException("장소 소유자만 OWNER 권한을 가질 수 있습니다.");
        }
        return MerchantPlaceMember.builder()
                .placeId(placeId)
                .userId(userId)
                .role(role)
                .status(MerchantPlaceMemberStatus.ACTIVE)
                .invitedBy(invitedBy)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void changeRole(MerchantPlaceMemberRole role, LocalDateTime now) {
        if (status != MerchantPlaceMemberStatus.ACTIVE) {
            throw new IllegalStateException("활성 팀원만 권한을 변경할 수 있습니다.");
        }
        if (role == MerchantPlaceMemberRole.OWNER) {
            throw new IllegalArgumentException("팀원에게 OWNER 권한을 부여할 수 없습니다.");
        }
        this.role = role;
        updatedAt = now;
    }

    public void revoke(LocalDateTime now) {
        status = MerchantPlaceMemberStatus.REVOKED;
        updatedAt = now;
    }

    public void reactivate(MerchantPlaceMemberRole role, Long invitedBy, LocalDateTime now) {
        if (status != MerchantPlaceMemberStatus.REVOKED) {
            throw new IllegalStateException("회수된 팀원만 재활성화할 수 있습니다.");
        }
        if (role == MerchantPlaceMemberRole.OWNER) {
            throw new IllegalArgumentException("팀원에게 OWNER 권한을 부여할 수 없습니다.");
        }
        this.role = role;
        this.invitedBy = invitedBy;
        this.status = MerchantPlaceMemberStatus.ACTIVE;
        this.updatedAt = now;
    }

    public void promoteToOwner(LocalDateTime now) {
        if (status != MerchantPlaceMemberStatus.ACTIVE) {
            throw new IllegalStateException("활성 팀원만 OWNER로 승격할 수 있습니다.");
        }
        this.role = MerchantPlaceMemberRole.OWNER;
        this.updatedAt = now;
    }

    /** 소유권 이전으로 회수된 기존 팀원을 동일한 장소의 OWNER로 복구합니다. */
    public void restoreAsOwner(Long userId, LocalDateTime now) {
        if (!this.userId.equals(userId)) {
            throw new IllegalArgumentException("동일한 사용자만 장소 소유자로 복구할 수 있습니다.");
        }
        role = MerchantPlaceMemberRole.OWNER;
        status = MerchantPlaceMemberStatus.ACTIVE;
        invitedBy = userId;
        updatedAt = now;
    }
}
