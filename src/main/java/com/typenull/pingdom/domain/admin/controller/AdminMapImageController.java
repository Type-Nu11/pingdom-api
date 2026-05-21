package com.typenull.pingdom.domain.admin.controller;

import com.typenull.pingdom.domain.admin.service.AdminPictureService;
import com.typenull.pingdom.global.config.security.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/map-images")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminMapImageController {

    private final AdminPictureService adminPictureService;

    @DeleteMapping("/{id}")
    @Operation(
            summary = "관리자 지도 이미지 삭제",
            description = "관리자가 지도 이미지 리소스를 강제로 삭제합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "지도 이미지 삭제 성공"
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
                    description = "사진을 찾을 수 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "사진을 찾을 수 없습니다.",
                                              "code": "PICTURE_NOT_FOUND"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<Void> forceDeleteMapImage(
            @Parameter(description = "강제 삭제할 지도 이미지 ID", example = "10") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        adminPictureService.deletePicture(id);
        if (adminUser != null) {
            log.info("Admin force deleted map image. adminUserId={}, imageId={}", adminUser.userId(), id);
        } else {
            log.info("Admin force deleted map image. adminUserId=unknown, imageId={}", id);
        }
        return ResponseEntity.noContent().build();
    }
}
