package com.typenull.pingdom.domain.map.controller;

import com.typenull.pingdom.domain.map.dto.MapImageResponse;
import com.typenull.pingdom.global.config.security.JwtAuthenticatedUser;
import com.typenull.pingdom.domain.map.dto.ImageUploadRequest;
import com.typenull.pingdom.domain.map.dto.MapImageUploadResponse;
import com.typenull.pingdom.domain.map.dto.PictureReportRequest;
import com.typenull.pingdom.domain.map.service.PictureReportService;
import com.typenull.pingdom.domain.map.service.S3Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.io.IOException;

@RestController
@RequestMapping("/map")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class MapImageController {

    private final S3Service s3Service;
    private final PictureReportService pictureReportService;

    @PostMapping(value = "/pictures/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "사진 업로드",
            description = "multipart/form-data로 사진 파일을 업로드하고 저장합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "사진 업로드 성공",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = "\"사진을 저장했습니다.\""
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청값 검증 실패",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "입력값을 확인해주세요.",
                                              "errors": {
                                                "file": "파일은 필수입니다."
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
                    responseCode = "500",
                    description = "업로드 처리 실패",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "업로드 과정에서 오류가 발생하였습니다.",
                                              "code": "UPLOAD_ERROR"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<MapImageResponse> upload(
            @Valid @ModelAttribute ImageUploadRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) throws IOException {
        Long userId = user.userId();
        return ResponseEntity.ok(s3Service.uploadImage(request,userId));
    }

    @DeleteMapping("/pictures/{id}/delete")
    @Operation(
            summary = "사진 삭제",
            description = "지정한 이미지 ID의 사진을 삭제합니다. 본인 소유 사진만 삭제할 수 있습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "사진 삭제 성공",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = "\"사진을 삭제했습니다.\""
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
                    responseCode = "403",
                    description = "본인 소유가 아닌 사진 삭제 시도",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "자신의 사진만 삭제할 수 있습니다.",
                                              "code": "OTHERS_NOT_DELETED"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "이미지를 찾을 수 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "이미지를 찾을 수 없습니다.",
                                              "code": "IMAGE_NOT_FOUND"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "S3 삭제 또는 연결 실패",
                    content = @Content(
                            examples = {
                                    @ExampleObject(
                                            name = "delete-error",
                                            value = """
                                                    {
                                                      "message": "이미지를 삭제하는 데 실패했습니다. 잠시 후 다시 시도해 주세요.",
                                                      "code": "DELETE_ERROR"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "s3-connection-error",
                                            value = """
                                                    {
                                                      "message": "S3 서버 연결에 실패했습니다.",
                                                      "code": "S3_CONNECTION_ERROR"
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    })
    public ResponseEntity<String> delete(
            @Parameter(description = "삭제할 이미지 ID", example = "1") @Valid @PathVariable("id") Long imageId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) throws IOException {
        Long userId = user.userId();
        s3Service.deleteImage(imageId,userId);
        return ResponseEntity.ok("사진을 삭제했습니다.");
    }

    @PostMapping("/pictures/{id}/report")
    @Operation(
            summary = "사진 신고",
            description = "지정한 이미지 ID의 사진을 신고합니다. 동일 사용자는 같은 사진을 한 번만 신고할 수 있습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "사진 신고 등록 성공",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = "\"사진 신고를 등록했습니다.\""
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청값 검증 실패",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "입력값을 확인해주세요.",
                                              "errors": {
                                                "reason": "신고 사유는 필수입니다."
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
                    description = "이미지를 찾을 수 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "이미지를 찾을 수 없습니다.",
                                              "code": "IMAGE_NOT_FOUND"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 신고한 사진",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "같은 사진은 한 번만 신고할 수 있습니다.",
                                              "code": "ALREADY_REPORTED_IMAGE"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<String> report(
            @Parameter(description = "신고할 이미지 ID", example = "1") @PathVariable("id") Long imageId,
            @Valid @RequestBody PictureReportRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        pictureReportService.report(imageId, user.userId(), user.username(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body("사진 신고를 등록했습니다.");
    }
}
