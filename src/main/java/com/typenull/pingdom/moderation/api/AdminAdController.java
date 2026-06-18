package com.typenull.pingdom.moderation.api;

import com.typenull.pingdom.moderation.api.dto.ad.AdminAdCreateRequest;
import com.typenull.pingdom.moderation.api.dto.ad.AdminAdCreateResponse;
import com.typenull.pingdom.moderation.application.AdminAdService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/ad")
@RequiredArgsConstructor
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminAdController {

    private final AdminAdService adminAdService;

    @PostMapping
    @Operation(
            summary = "이벤트/광고 등록",
            description = "관리자가 앱에 노출할 이벤트/광고 배너 정보를 등록합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "이벤트/광고 등록 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminAdCreateResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "adId": 1,
                                              "title": "여름 한정 출석 이벤트",
                                              "startAt": "2026-06-20T09:00:00",
                                              "endAt": "2026-06-30T23:59:59",
                                              "message": "이벤트/광고를 등록했습니다."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패 또는 기간 설정 오류",
                    content = @Content(
                            examples = {
                                    @ExampleObject(
                                            name = "validation-failure",
                                            value = """
                                                    {
                                                      "message": "입력값을 확인해주세요.",
                                                      "errors": {
                                                        "title": "제목은 필수입니다."
                                                      }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "invalid-period",
                                            value = """
                                                    {
                                                      "message": "이벤트/광고 종료 시각은 시작 시각보다 이후여야 합니다.",
                                                      "code": "AD_INVALID_PERIOD"
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
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
                    responseCode = "403",
                    description = "관리자 권한 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "관리자 권한이 필요합니다.",
                                              "code": "ACCESS_DENIED"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<AdminAdCreateResponse> createAd(
            @Valid @RequestBody AdminAdCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminAdService.create(request));
    }

    @DeleteMapping("/{adId}")
    @Operation(
            summary = "이벤트/광고 삭제",
            description = "관리자가 등록된 이벤트/광고를 삭제합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "이벤트/광고 삭제 성공"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
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
                    responseCode = "403",
                    description = "관리자 권한 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "관리자 권한이 필요합니다.",
                                              "code": "ACCESS_DENIED"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "이벤트/광고를 찾을 수 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "이벤트/광고를 찾을 수 없습니다.",
                                              "code": "AD_NOT_FOUND"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<Void> deleteAd(
            @Parameter(description = "삭제할 이벤트/광고 ID", example = "1")
            @PathVariable("adId") Long adId
    ) {
        adminAdService.delete(adId);
        return ResponseEntity.noContent().build();
    }
}
