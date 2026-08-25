package com.typenull.pingdom.identity.api.merchant;

import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.annotation.AuthenticatedOnly;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantTeamInvitationResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantTeamInviteRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantTeamMemberResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantTeamRoleUpdateRequest;
import com.typenull.pingdom.identity.application.service.merchant.MerchantTeamService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.api.dto.ValidationErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
@AuthenticatedOnly
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Merchant", description = "Merchant 전용 API")
public class MerchantTeamController {
    private final MerchantTeamService teamService;

    @GetMapping("/places/{placeId}/members")
    @Operation(summary = "Merchant 장소 팀원 목록 조회", description = "OWNER 또는 MANAGER만 활성 상태의 팀원을 ID 오름차순으로 조회할 수 있습니다. REVOKED 팀원과 만료·취소된 초대는 목록에 포함되지 않으며, 권한 부족은 MERCHANT_TEAM_PERMISSION_REQUIRED로 응답합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "팀원 목록 조회 성공", content = @Content(array = @ArraySchema(schema = @Schema(implementation = MerchantTeamMemberResponse.class)))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "INVALID_TOKEN", value = "{\"message\":\"유효하지 않은 토큰입니다.\",\"code\":\"INVALID_TOKEN\"}"))),
            @ApiResponse(responseCode = "403", description = "OWNER 또는 MANAGER 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "MERCHANT_TEAM_PERMISSION_REQUIRED", value = "{\"message\":\"Merchant 팀원 관리 권한이 필요합니다.\",\"code\":\"MERCHANT_TEAM_PERMISSION_REQUIRED\"}")))
    })
    public List<MerchantTeamMemberResponse> list(@PathVariable Long placeId,
                                                  @CurrentUser JwtAuthenticatedUser user) {
        return teamService.list(user.userId(), placeId);
    }

    @PostMapping("/places/{placeId}/members/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Merchant 장소 팀원 초대", description = "OWNER 또는 MANAGER가 STAFF 또는 MANAGER 역할로 초대할 수 있습니다. expiresAt을 생략하면 생성 시각 기준 7일 후 만료됩니다. 활성 팀원 또는 같은 대상의 PENDING 초대가 있으면 409입니다. REVOKED 팀원은 새 초대 수락 시 재활성화됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "팀원 초대 생성 성공", content = @Content(schema = @Schema(implementation = MerchantTeamInvitationResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력값, OWNER 역할 또는 만료 시각이 올바르지 않음", content = @Content(schema = @Schema(oneOf = {ErrorResponse.class, ValidationErrorResponse.class}), examples = @ExampleObject(name = "MERCHANT_TEAM_INVITATION_INVALID_EXPIRATION", value = "{\"message\":\"Merchant 팀원 초대 만료 시각이 올바르지 않습니다.\",\"code\":\"MERCHANT_TEAM_INVITATION_INVALID_EXPIRATION\"}"))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "OWNER 또는 MANAGER 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "초대 대상을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "활성 팀원이거나 대기 중인 초대가 이미 존재함", content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "MERCHANT_TEAM_INVITATION_ALREADY_EXISTS", value = "{\"message\":\"대기 중인 Merchant 팀원 초대가 이미 존재합니다.\",\"code\":\"MERCHANT_TEAM_INVITATION_ALREADY_EXISTS\"}")))
    })
    public MerchantTeamInvitationResponse invite(@PathVariable Long placeId,
                                                  @Valid @RequestBody MerchantTeamInviteRequest request,
                                                  @CurrentUser JwtAuthenticatedUser user) {
        return teamService.invite(user.userId(), placeId, request);
    }

    @PatchMapping("/places/{placeId}/members/{memberId}")
    @Operation(summary = "Merchant 장소 팀원 권한 변경", description = "OWNER 또는 MANAGER가 활성 팀원의 역할을 변경합니다. OWNER 역할은 팀원에게 부여할 수 없으며, 비활성 팀원은 변경할 수 없습니다. 잘못된 역할은 MERCHANT_TEAM_INVITATION_INVALID_ROLE, 비활성 팀원은 MERCHANT_TEAM_MEMBER_NOT_ACTIVE, 대상 없음은 MERCHANT_TEAM_MEMBER_NOT_FOUND로 응답합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "팀원 역할 변경 성공", content = @Content(schema = @Schema(implementation = MerchantTeamMemberResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력 역할이 올바르지 않음", content = @Content(schema = @Schema(oneOf = {ErrorResponse.class, ValidationErrorResponse.class}))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "OWNER 또는 MANAGER 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "장소의 팀원을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "비활성 팀원의 역할은 변경할 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "MERCHANT_TEAM_MEMBER_NOT_ACTIVE", value = "{\"message\":\"활성 상태의 Merchant 팀원만 변경할 수 있습니다.\",\"code\":\"MERCHANT_TEAM_MEMBER_NOT_ACTIVE\"}")))
    })
    public MerchantTeamMemberResponse updateRole(@PathVariable Long placeId, @PathVariable Long memberId,
                                                  @Valid @RequestBody MerchantTeamRoleUpdateRequest request,
                                                  @CurrentUser JwtAuthenticatedUser user) {
        return teamService.updateRole(user.userId(), placeId, memberId, request);
    }

    @PostMapping("/invitations/{invitationId}/accept")
    @Operation(summary = "Merchant 장소 팀원 초대 수락", description = "초대 수신자만 수락할 수 있습니다. 이미 수락된 초대는 활성 팀원이 존재하면 동일한 팀원 응답을 반환합니다. 초대 수락은 초대와 기존 팀원을 잠가 동시 요청에서도 중복 팀원을 만들지 않으며, 만료 초대는 410 MERCHANT_TEAM_INVITATION_EXPIRED로 응답합니다. 수신자 불일치는 MERCHANT_TEAM_PERMISSION_REQUIRED, 비활성 계정은 MERCHANT_TEAM_MEMBER_NOT_ACTIVE로 구분합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "팀원 초대 수락 성공 또는 반복 수락의 멱등 응답", content = @Content(schema = @Schema(implementation = MerchantTeamMemberResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "초대 수신자가 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "초대를 찾을 수 없거나 수락된 초대에 활성 팀원이 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "수신자 계정이 비활성 상태이거나 기존 팀원을 재활성화할 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "410", description = "초대가 만료됨", content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "MERCHANT_TEAM_INVITATION_EXPIRED", value = "{\"message\":\"Merchant 팀원 초대가 만료되었습니다.\",\"code\":\"MERCHANT_TEAM_INVITATION_EXPIRED\"}")))
    })
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
