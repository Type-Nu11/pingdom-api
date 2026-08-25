package com.typenull.pingdom.identity.application.service.merchant;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantTeamInvitationResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantTeamInviteRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantTeamMemberResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantTeamRoleUpdateRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.exception.UsersErrorCode;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInvitation;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInvitationStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMember;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberRole;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceInvitationRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceMemberRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

@Service
@RequiredArgsConstructor
/** 장소별 Merchant 팀원 초대·역할 변경·수락·철회를 권한 정책과 함께 처리합니다. */
public class MerchantTeamService {
    private final MerchantPlaceMemberRepository memberRepository;
    private final MerchantPlaceInvitationRepository invitationRepository;
    private final MerchantOwnerPlaceRepository ownerPlaceRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    // 관리자 권한을 확인하고 장소의 활성 팀원을 조회합니다.
    public List<MerchantTeamMemberResponse> list(Long actorId, Long placeId) {
        requireManager(actorId, placeId);
        return memberRepository.findAllByPlaceIdAndStatusOrderByIdAsc(placeId, MerchantPlaceMemberStatus.ACTIVE)
                .stream().map(MerchantTeamMemberResponse::from).toList();
    }

    @Transactional
    // 초대 대상과 역할을 검증한 뒤 만료 시각이 있는 팀 초대를 생성합니다.
    public MerchantTeamInvitationResponse invite(Long actorId, Long placeId, MerchantTeamInviteRequest request) {
        lockPlaceOwnership(placeId);
        requireManager(actorId, placeId);
        if (request.role() == MerchantPlaceMemberRole.OWNER) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_INVITATION_INVALID_ROLE);
        }
        User invitee = userRepository.findById(request.inviteeUserId())
                .orElseThrow(() -> new UsersException(UsersErrorCode.USER_NOT_FOUND));
        if (invitee.isWithdrawn()) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_MEMBER_NOT_ACTIVE);
        }
        memberRepository.findByPlaceIdAndUserId(placeId, request.inviteeUserId()).ifPresent(member -> {
            if (member.getStatus() == MerchantPlaceMemberStatus.ACTIVE) {
                throw new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_MEMBER_ALREADY_EXISTS);
            }
        });
        if (invitationRepository.existsByPlaceIdAndInviteeUserIdAndStatus(
                placeId, request.inviteeUserId(), MerchantPlaceInvitationStatus.PENDING)) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_INVITATION_ALREADY_EXISTS);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expiresAt = request.expiresAt() == null ? now.plusDays(7) : request.expiresAt();
        if (!expiresAt.isAfter(now)) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_INVITATION_INVALID_EXPIRATION);
        }
        try {
            return MerchantTeamInvitationResponse.from(invitationRepository.save(
                    MerchantPlaceInvitation.pending(placeId, request.inviteeUserId(), actorId, request.role(), expiresAt, now)));
        } catch (DataIntegrityViolationException exception) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_INVITATION_ALREADY_EXISTS);
        }
    }

    @Transactional
    // 장소 팀원의 역할 변경 권한과 도메인 상태를 검증합니다.
    public MerchantTeamMemberResponse updateRole(Long actorId, Long placeId, Long memberId,
                                                  MerchantTeamRoleUpdateRequest request) {
        lockPlaceOwnership(placeId);
        requireManager(actorId, placeId);
        MerchantPlaceMember member = memberRepository.findById(memberId)
                .filter(candidate -> candidate.getPlaceId().equals(placeId))
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_MEMBER_NOT_FOUND));
        try {
            member.changeRole(request.role(), LocalDateTime.now(clock));
        } catch (IllegalStateException exception) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_MEMBER_NOT_ACTIVE);
        } catch (IllegalArgumentException exception) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_INVITATION_INVALID_ROLE);
        }
        return MerchantTeamMemberResponse.from(member);
    }

    @Transactional
    // 초대 수신자 본인인지와 계정 상태를 확인한 뒤 초대를 수락합니다.
    public MerchantTeamMemberResponse acceptInvitation(Long actorId, Long invitationId) {
        MerchantPlaceInvitation invitationSnapshot = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_INVITATION_NOT_FOUND));
        lockPlaceOwnership(invitationSnapshot.getPlaceId());
        MerchantPlaceInvitation invitation = invitationRepository.findByIdForUpdate(invitationId)
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_INVITATION_NOT_FOUND));
        if (!invitation.getInviteeUserId().equals(actorId)) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_PERMISSION_REQUIRED);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        User invitee = userRepository.findById(actorId)
                .orElseThrow(() -> new UsersException(UsersErrorCode.USER_NOT_FOUND));
        if (invitee.isWithdrawn() || invitee.isCurrentlyBanned(now)) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_MEMBER_NOT_ACTIVE);
        }
        var existingMember = memberRepository.findByPlaceIdAndUserIdForUpdate(invitation.getPlaceId(), actorId);
        if (invitation.getStatus() == MerchantPlaceInvitationStatus.ACCEPTED) {
            return existingMember.filter(member -> member.getStatus() == MerchantPlaceMemberStatus.ACTIVE)
                    .map(MerchantTeamMemberResponse::from)
                    .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_INVITATION_NOT_FOUND));
        }
        try {
            invitation.accept(now);
        } catch (IllegalStateException exception) {
            if (invitation.getStatus() == MerchantPlaceInvitationStatus.EXPIRED) {
                throw new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_INVITATION_EXPIRED);
            }
            throw new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_INVITATION_NOT_FOUND);
        }
        MerchantPlaceMember member = existingMember.orElseGet(() ->
                MerchantPlaceMember.create(invitation.getPlaceId(), actorId, invitation.getRole(), invitation.getInvitedBy(), now));
        if (existingMember.isPresent()) {
            try {
                member.reactivate(invitation.getRole(), invitation.getInvitedBy(), now);
            } catch (IllegalStateException exception) {
                throw new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_MEMBER_ALREADY_EXISTS);
            }
        }
        return MerchantTeamMemberResponse.from(memberRepository.save(member));
    }

    @Transactional
    // 관리자가 소유자가 아닌 팀원을 비활성화합니다.
    public void revoke(Long actorId, Long placeId, Long memberId) {
        lockPlaceOwnership(placeId);
        requireManager(actorId, placeId);
        MerchantPlaceMember member = memberRepository.findById(memberId)
                .filter(candidate -> candidate.getPlaceId().equals(placeId))
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_MEMBER_NOT_FOUND));
        if (member.getRole() == MerchantPlaceMemberRole.OWNER) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_PERMISSION_REQUIRED);
        }
        member.revoke(LocalDateTime.now(clock));
    }

    // 장소 소유자 또는 관리자 역할인지 확인하고 아니면 권한 예외를 발생시킵니다.
    private void requireManager(Long actorId, Long placeId) {
        MerchantPlaceMember member = memberRepository.findByPlaceIdAndUserId(placeId, actorId)
                .orElseGet(() -> ownerPlaceRepository.findById(placeId)
                        .filter(owner -> owner.getMerchantOwnerUserId().equals(actorId))
                        .map(owner -> MerchantPlaceMember.owner(placeId, actorId, LocalDateTime.now(clock)))
                        .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_PERMISSION_REQUIRED)));
        if (member.getStatus() != MerchantPlaceMemberStatus.ACTIVE
                || (member.getRole() != MerchantPlaceMemberRole.OWNER && member.getRole() != MerchantPlaceMemberRole.MANAGER)) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_PERMISSION_REQUIRED);
        }
    }

    private void lockPlaceOwnership(Long placeId) {
        ownerPlaceRepository.findByPlaceIdForUpdate(placeId)
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_PERMISSION_REQUIRED));
    }
}
