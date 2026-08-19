package com.typenull.pingdom.identity.api.user;

import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.identity.api.dto.profile.ChangePasswordRequest;
import com.typenull.pingdom.identity.api.dto.profile.ChangeUsernameRequest;
import com.typenull.pingdom.identity.api.dto.profile.MyPageResponse;
import com.typenull.pingdom.identity.api.dto.profile.TravelPurposePreferenceResponse;
import com.typenull.pingdom.identity.api.dto.profile.TravelPurposePreferenceUpdateRequest;
import com.typenull.pingdom.identity.api.dto.export.UserDataExportResponse;
import com.typenull.pingdom.identity.application.command.ChangeInfoService;
import com.typenull.pingdom.identity.application.query.MyPageQueryResult;
import com.typenull.pingdom.identity.application.query.MyPageService;
import com.typenull.pingdom.identity.application.query.UserDataExportResult;
import com.typenull.pingdom.identity.application.query.UserDataExportService;
import com.typenull.pingdom.identity.application.service.travel.TravelPurposePreferenceService;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class UsersController {

    private final MyPageService myPageService;
    private final UserDataExportService userDataExportService;
    private final ChangeInfoService changeInfoService;
    private final TravelPurposePreferenceService travelPurposePreferenceService;

    @GetMapping("/me")
    @Operation(
            summary = "내 정보 조회",
            description = "현재 인증된 사용자의 마이페이지 정보를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = MyPageResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "id": 1,
                                              "username": "pingdom_user",
                                              "email": "pingdom@example.com",
                                              "birthYear": 1998,
                                              "profileImageUrl": "https://cdn.pingdom.com/profiles/user1.png",
                                              "language": "ko",
                                              "country": "KR"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 요청 또는 유효하지 않은 토큰",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "유효하지 않은 토큰입니다.",
                                              "code": "INVALID_TOKEN"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "사용자를 찾을 수 없습니다.",
                                              "code": "USER_NOT_FOUND"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<MyPageResponse> getMyPageInfo(
            @CurrentUser JwtAuthenticatedUser user
    ) {
        MyPageQueryResult result = myPageService.getMyPageInfo(authenticatedUserId(user));
        return ResponseEntity.ok(MyPageResponse.from(result));
    }

    @GetMapping("/me/travel-purposes")
    @Operation(summary = "여행 목적 선호 조회", description = "현재 인증된 사용자의 여행 목적 선호 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = TravelPurposePreferenceResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 요청 또는 유효하지 않은 토큰",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<TravelPurposePreferenceResponse> getTravelPurposes(
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return ResponseEntity.ok(new TravelPurposePreferenceResponse(
                travelPurposePreferenceService.getTravelPurposes(authenticatedUserId(user))
        ));
    }

    @PutMapping("/me/travel-purposes")
    @Operation(summary = "여행 목적 선호 전체 변경", description = "현재 인증된 사용자의 여행 목적 선호를 요청 목록으로 전체 교체합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "변경 성공",
                    content = @Content(schema = @Schema(implementation = TravelPurposePreferenceResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 값 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 요청 또는 유효하지 않은 토큰",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<TravelPurposePreferenceResponse> replaceTravelPurposes(
            @Valid @RequestBody TravelPurposePreferenceUpdateRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return ResponseEntity.ok(new TravelPurposePreferenceResponse(
                travelPurposePreferenceService.replaceTravelPurposes(
                        authenticatedUserId(user),
                        request.travelPurposes()
                )
        ));
    }

    @GetMapping("/me/export")
    @Operation(
            summary = "내 데이터 내보내기",
            description = "현재 인증된 사용자의 계정 정보, 전체 북마크, 최근 좋아요한 지도 이미지 ID 최대 50개, 여행 일정, Claim 이력과 만료되지 않은 현재 행동 의도를 JSON으로 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "내보내기 성공",
                    content = @Content(
                            schema = @Schema(implementation = UserDataExportResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "user": {
                                                "id": 1,
                                                "username": "pingdom_user",
                                                "profileImageUrl": "https://cdn.pingdom.com/profiles/user1.png"
                                              },
                                              "bookmarks": [
                                                {
                                                  "id": 10,
                                                  "placeId": 123
                                                }
                                              ],
                                              "likedMapImageIds": [981, 812, 700],
                                              "travelSchedules": [
                                                {
                                                  "id": 31,
                                                  "startDate": "2026-08-01",
                                                  "endDate": "2026-08-03",
                                                  "state": "SCHEDULED"
                                                }
                                              ],
                                              "currentActivityIntent": {
                                                "intent": "CAFE",
                                                "expiresAt": "2026-08-01T14:00:00"
                                              },
                                              "merchantPlaceClaims": [
                                                {
                                                  "id": 40,
                                                  "placeId": 123,
                                                  "status": "REJECTED",
                                                  "claimReason": "매장 운영자입니다.",
                                                  "reviewReason": "사업자 주소 불일치",
                                                  "reviewedAt": "2026-07-16T10:00:00",
                                                  "createdAt": "2026-07-15T14:00:00"
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 요청 또는 유효하지 않은 토큰",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "유효하지 않은 토큰입니다.",
                                              "code": "INVALID_TOKEN"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "사용자를 찾을 수 없습니다.",
                                              "code": "USER_NOT_FOUND"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<UserDataExportResponse> exportMyData(
            @CurrentUser JwtAuthenticatedUser user
    ) {
        UserDataExportResult result = userDataExportService.exportMyData(authenticatedUserId(user));
        return ResponseEntity.ok(UserDataExportResponse.from(result));
    }

    @PostMapping("/change-pw")
    @Operation(
            summary = "비밀번호 변경",
            description = "현재 비밀번호를 확인한 뒤 새 비밀번호로 변경합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "비밀번호 변경 성공",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = "\"비밀번호 변경 완료\""
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패 또는 새 비밀번호 확인 불일치",
                    content = @Content(
                            examples = {
                                    @ExampleObject(
                                            name = "validation-failure",
                                            value = """
                                                    {
                                                      "message": "입력값을 확인해주세요.",
                                                      "errors": {
                                                        "newPassword": "비밀번호는 8자 이상이어야 합니다."
                                                      }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "password-mismatch",
                                            value = """
                                                    {
                                                      "message": "비밀번호가 서로 다릅니다.",
                                                      "code": "PASSWORD_MISMATCH"
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "현재 비밀번호 불일치 또는 유효하지 않은 토큰",
                    content = @Content(
                            examples = {
                                    @ExampleObject(
                                            name = "invalid-credentials",
                                            value = """
                                                    {
                                                      "message": "아이디 또는 비밀번호가 올바르지 않습니다.",
                                                      "code": "INVALID_CREDENTIALS"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "invalid-token",
                                            value = """
                                                    {
                                                      "message": "유효하지 않은 토큰입니다.",
                                                      "code": "INVALID_TOKEN"
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "사용자를 찾을 수 없습니다.",
                                              "code": "USER_NOT_FOUND"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<String> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        changeInfoService.changePassword(request, authenticatedUserId(user));
        return ResponseEntity.ok("비밀번호 변경 완료");
    }

    @PostMapping("/change-id")
    @Operation(
            summary = "아이디 변경",
            description = "현재 인증된 사용자의 아이디를 새 아이디로 변경합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "아이디 변경 성공",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = "\"아이디 변경 완료\""
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "입력값을 확인해주세요.",
                                              "errors": {
                                                "newUsername": "아이디는 4자 이상 50자 이하여야 합니다."
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 토큰",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "유효하지 않은 토큰입니다.",
                                              "code": "INVALID_TOKEN"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "사용자를 찾을 수 없습니다.",
                                              "code": "USER_NOT_FOUND"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 사용 중인 아이디",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "이미 있는 아이디입니다.",
                                              "code": "USERNAME_ALREADY_EXISTS"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<String> changeUsername(
            @Valid @RequestBody ChangeUsernameRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        changeInfoService.changeUsername(request, authenticatedUserId(user));
        return ResponseEntity.ok("아이디 변경 완료");
    }

    private Long authenticatedUserId(JwtAuthenticatedUser user) {
        if (user == null) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
        return user.userId();
    }
}
