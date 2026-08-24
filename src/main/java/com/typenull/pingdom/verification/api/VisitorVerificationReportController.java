package com.typenull.pingdom.verification.api;

import com.typenull.pingdom.shared.security.annotation.AuthenticatedOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.api.dto.ValidationErrorResponse;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import com.typenull.pingdom.verification.api.dto.*;
import com.typenull.pingdom.verification.application.VisitorVerificationReportCorrectionService;
import com.typenull.pingdom.verification.application.VisitorVerificationReportService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/visitor-verification-reports")
@RequiredArgsConstructor
@AuthenticatedOnly
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "App", description = "앱 전용 API")
@org.springframework.validation.annotation.Validated
public class VisitorVerificationReportController {
    private final VisitorVerificationReportService service;
    private final VisitorVerificationReportCorrectionService correctionService;

    @PostMapping
    @Operation(
            summary = "방문자 검증 제보 생성",
            description = """
                    활성 일반 사용자가 장소와 제보 유형별로 처리 중인 제보가 없을 때 새 제보를 제출합니다.
                    구조화 필드는 reportType과 일치해야 하며, 같은 장소·유형의 SUBMITTED 제보가 있으면 409를 반환합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "제보 생성 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 검증 실패 또는 제보 유형과 구조화 정보 불일치",
                    content = @Content(
                            schema = @Schema(oneOf = {ValidationErrorResponse.class, ErrorResponse.class}),
                            examples = {
                                    @ExampleObject(
                                            name = "VALIDATION_FAILED",
                                            value = """
                                                    {"message":"입력값을 확인해주세요.","code":"VALIDATION_FAILED","errors":{"description":"공백일 수 없습니다."}}
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "INVALID_REPORT_DETAILS",
                                            value = """
                                                    {"message":"제보 유형과 구조화 정보가 일치하지 않습니다.","code":"INVALID_REPORT_DETAILS"}
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않았거나 만료된 토큰",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "INVALID_TOKEN",
                                            value = """
                                                    {"message":"유효하지 않은 토큰입니다.","code":"INVALID_TOKEN"}
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "EXPIRED_TOKEN",
                                            value = """
                                                    {"message":"만료된 토큰입니다.","code":"EXPIRED_TOKEN"}
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "활성 일반 사용자 계정이 아님 (TOURIST_ACCOUNT_REQUIRED)",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "TOURIST_ACCOUNT_REQUIRED",
                                    value = """
                                            {"message":"활성 일반 사용자만 제보할 수 있습니다.","code":"TOURIST_ACCOUNT_REQUIRED"}
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "장소를 찾을 수 없음 (PLACE_NOT_FOUND)",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "PLACE_NOT_FOUND",
                                    value = """
                                            {"message":"장소를 찾을 수 없습니다.","code":"PLACE_NOT_FOUND"}
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "같은 장소·유형의 처리 중 제보가 이미 있음 (ACTIVE_REPORT_ALREADY_EXISTS)",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "ACTIVE_REPORT_ALREADY_EXISTS",
                                    value = """
                                            {"message":"같은 장소와 유형의 처리 중인 제보가 이미 있습니다.","code":"ACTIVE_REPORT_ALREADY_EXISTS"}
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<MyVisitorVerificationReportResponse> submit(
            @Valid @RequestBody VisitorVerificationReportCreateRequest request,
            @CurrentUser JwtAuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.submit(user.userId(), request));
    }

    @GetMapping
    @Operation(summary = "내 방문자 검증 제보 목록 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "내 제보 목록 조회 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "페이지 요청 값 검증 실패 (VALIDATION_FAILED)",
                    content = @Content(
                            schema = @Schema(implementation = ValidationErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "VALIDATION_FAILED",
                                    value = """
                                            {"message":"입력값을 확인해주세요.","code":"VALIDATION_FAILED","errors":{"page":"1 이상이어야 합니다."}}
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않았거나 만료된 토큰",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "INVALID_TOKEN",
                                            value = """
                                                    {"message":"유효하지 않은 토큰입니다.","code":"INVALID_TOKEN"}
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "EXPIRED_TOKEN",
                                            value = """
                                                    {"message":"만료된 토큰입니다.","code":"EXPIRED_TOKEN"}
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "활성 일반 사용자 계정이 아님 (TOURIST_ACCOUNT_REQUIRED)",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "TOURIST_ACCOUNT_REQUIRED",
                                    value = """
                                            {"message":"활성 일반 사용자만 제보할 수 있습니다.","code":"TOURIST_ACCOUNT_REQUIRED"}
                                            """
                            )
                    )
            )
    })
    public MyVisitorVerificationReportPageResponse listMine(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @CurrentUser JwtAuthenticatedUser user) {
        return service.listMine(user.userId(), page, limit);
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "내 방문자 검증 제보 상세 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "내 제보 상세 조회 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않았거나 만료된 토큰",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "INVALID_TOKEN",
                                            value = """
                                                    {"message":"유효하지 않은 토큰입니다.","code":"INVALID_TOKEN"}
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "EXPIRED_TOKEN",
                                            value = """
                                                    {"message":"만료된 토큰입니다.","code":"EXPIRED_TOKEN"}
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "활성 일반 사용자 계정이 아니거나 본인 소유 제보가 아님",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "TOURIST_ACCOUNT_REQUIRED",
                                            value = """
                                                    {"message":"활성 일반 사용자만 제보할 수 있습니다.","code":"TOURIST_ACCOUNT_REQUIRED"}
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "REPORT_FORBIDDEN",
                                            value = """
                                                    {"message":"이 제보를 조회할 권한이 없습니다.","code":"REPORT_FORBIDDEN"}
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "제보를 찾을 수 없음 (REPORT_NOT_FOUND)",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "REPORT_NOT_FOUND",
                                    value = """
                                            {"message":"방문자 검증 제보를 찾을 수 없습니다.","code":"REPORT_NOT_FOUND"}
                                            """
                            )
                    )
            )
    })
    public MyVisitorVerificationReportResponse getMine(@PathVariable Long reportId,
            @CurrentUser JwtAuthenticatedUser user) {
        return service.getMine(user.userId(), reportId);
    }

    @PostMapping("/{reportId}/corrections")
    @Operation(
            summary = "방문자 검증 제보 정정 제출",
            description = """
                    제보 소유자만 정정을 제출할 수 있습니다.
                    원본 제보 상태가 ACCEPTED 또는 REJECTED일 때만 허용되며, 처리 중인 정정이 있으면 새 정정을 제출할 수 없습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "제보 정정 제출 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 검증 실패 또는 정정 내용 불일치",
                    content = @Content(
                            schema = @Schema(oneOf = {ValidationErrorResponse.class, ErrorResponse.class}),
                            examples = {
                                    @ExampleObject(
                                            name = "VALIDATION_FAILED",
                                            value = """
                                                    {"message":"입력값을 확인해주세요.","code":"VALIDATION_FAILED","errors":{"description":"공백일 수 없습니다."}}
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "INVALID_CORRECTION_DETAILS",
                                            value = """
                                                    {"message":"제보 정정 내용이 올바르지 않습니다.","code":"INVALID_CORRECTION_DETAILS"}
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않았거나 만료된 토큰",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "INVALID_TOKEN",
                                            value = """
                                                    {"message":"유효하지 않은 토큰입니다.","code":"INVALID_TOKEN"}
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "EXPIRED_TOKEN",
                                            value = """
                                                    {"message":"만료된 토큰입니다.","code":"EXPIRED_TOKEN"}
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "활성 일반 사용자 계정이 아니거나 본인 소유 제보가 아님",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "TOURIST_ACCOUNT_REQUIRED",
                                            value = """
                                                    {"message":"활성 일반 사용자만 제보할 수 있습니다.","code":"TOURIST_ACCOUNT_REQUIRED"}
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "REPORT_FORBIDDEN",
                                            value = """
                                                    {"message":"이 제보를 조회할 권한이 없습니다.","code":"REPORT_FORBIDDEN"}
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "제보를 찾을 수 없음 (REPORT_NOT_FOUND)",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "REPORT_NOT_FOUND",
                                    value = """
                                            {"message":"방문자 검증 제보를 찾을 수 없습니다.","code":"REPORT_NOT_FOUND"}
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "정정 불가 상태 또는 처리 중인 정정 존재",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "CORRECTION_NOT_ALLOWED",
                                            value = """
                                                    {"message":"현재 제보 상태에서는 정정을 제출할 수 없습니다.","code":"CORRECTION_NOT_ALLOWED"}
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "ACTIVE_CORRECTION_ALREADY_EXISTS",
                                            value = """
                                                    {"message":"처리 중인 제보 정정이 이미 있습니다.","code":"ACTIVE_CORRECTION_ALREADY_EXISTS"}
                                                    """
                                    )
                            }
                    )
            )
    })
    public ResponseEntity<MyVisitorVerificationReportCorrectionResponse> submitCorrection(
            @PathVariable Long reportId,
            @Valid @RequestBody VisitorVerificationReportCorrectionRequest request,
            @CurrentUser JwtAuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(correctionService.submit(user.userId(), reportId, request));
    }

    @GetMapping("/{reportId}/corrections")
    @Operation(summary = "내 방문자 검증 제보 정정 이력 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "제보 정정 이력 조회 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "페이지 요청 값 검증 실패 (VALIDATION_FAILED)",
                    content = @Content(
                            schema = @Schema(implementation = ValidationErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "VALIDATION_FAILED",
                                    value = """
                                            {"message":"입력값을 확인해주세요.","code":"VALIDATION_FAILED","errors":{"page":"1 이상이어야 합니다."}}
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않았거나 만료된 토큰",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "INVALID_TOKEN",
                                            value = """
                                                    {"message":"유효하지 않은 토큰입니다.","code":"INVALID_TOKEN"}
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "EXPIRED_TOKEN",
                                            value = """
                                                    {"message":"만료된 토큰입니다.","code":"EXPIRED_TOKEN"}
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "활성 일반 사용자 계정이 아니거나 본인 소유 제보가 아님",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "TOURIST_ACCOUNT_REQUIRED",
                                            value = """
                                                    {"message":"활성 일반 사용자만 제보할 수 있습니다.","code":"TOURIST_ACCOUNT_REQUIRED"}
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "CORRECTION_FORBIDDEN",
                                            value = """
                                                    {"message":"이 제보 정정을 조회할 권한이 없습니다.","code":"CORRECTION_FORBIDDEN"}
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "제보를 찾을 수 없음 (REPORT_NOT_FOUND)",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "REPORT_NOT_FOUND",
                                    value = """
                                            {"message":"방문자 검증 제보를 찾을 수 없습니다.","code":"REPORT_NOT_FOUND"}
                                            """
                            )
                    )
            )
    })
    public MyVisitorVerificationReportCorrectionPageResponse listCorrections(
            @PathVariable Long reportId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @CurrentUser JwtAuthenticatedUser user) {
        return correctionService.listMine(user.userId(), reportId, page, limit);
    }
}
