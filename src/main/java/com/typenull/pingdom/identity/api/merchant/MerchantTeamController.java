package com.typenull.pingdom.identity.api.merchant;

import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantTeamInvitationResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantTeamInviteRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantTeamMemberResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantTeamRoleUpdateRequest;
import com.typenull.pingdom.identity.application.service.merchant.MerchantTeamService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/merchant-owner")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class MerchantTeamController {
    private final MerchantTeamService teamService;

    @GetMapping("/places/{placeId}/members")
    @Operation(summary = "Merchant 장소 팀원 목록 조회")
    public List<MerchantTeamMemberResponse> list(@PathVariable Long placeId,
                                                  @CurrentUser JwtAuthenticatedUser user) {
        return teamService.list(user.userId(), placeId);
    }

    @PostMapping("/places/{placeId}/members/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Merchant 장소 팀원 초대")
    @ApiResponse(responseCode = "201", description = "팀원 초대 생성 성공")
    public MerchantTeamInvitationResponse invite(@PathVariable Long placeId,
                                                  @Valid @RequestBody MerchantTeamInviteRequest request,
                                                  @CurrentUser JwtAuthenticatedUser user) {
        return teamService.invite(user.userId(), placeId, request);
    }

    @PatchMapping("/places/{placeId}/members/{memberId}")
    @Operation(summary = "Merchant 장소 팀원 권한 변경")
    public MerchantTeamMemberResponse updateRole(@PathVariable Long placeId, @PathVariable Long memberId,
                                                  @Valid @RequestBody MerchantTeamRoleUpdateRequest request,
                                                  @CurrentUser JwtAuthenticatedUser user) {
        return teamService.updateRole(user.userId(), placeId, memberId, request);
    }

    @PostMapping("/invitations/{invitationId}/accept")
    @Operation(summary = "Merchant 장소 팀원 초대 수락")
    public MerchantTeamMemberResponse accept(@PathVariable Long invitationId,
                                             @CurrentUser JwtAuthenticatedUser user) {
        return teamService.acceptInvitation(user.userId(), invitationId);
    }

    @DeleteMapping("/places/{placeId}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Merchant 장소 팀원 권한 회수")
    public void revoke(@PathVariable Long placeId, @PathVariable Long memberId,
                       @CurrentUser JwtAuthenticatedUser user) {
        teamService.revoke(user.userId(), placeId, memberId);
    }
}
