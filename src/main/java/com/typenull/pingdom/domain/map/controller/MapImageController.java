package com.typenull.pingdom.domain.map.controller;

import com.typenull.pingdom.global.config.security.JwtAuthenticatedUser;
import com.typenull.pingdom.domain.map.dto.ImageUploadRequest;
import com.typenull.pingdom.domain.map.dto.PictureReportRequest;
import com.typenull.pingdom.domain.map.service.PictureReportService;
import com.typenull.pingdom.domain.map.service.S3Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/map")
@RequiredArgsConstructor
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
    public ResponseEntity<String> upload(
            @Valid @ModelAttribute ImageUploadRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) throws IOException {
        Long userId = user.userId();
        s3Service.uploadImage(request,userId);
        return ResponseEntity.ok("사진을 저장했습니다.");
    }

    @DeleteMapping("/pictures/{id}/delete")
    public ResponseEntity<String> delete(@Valid @PathVariable("id") Long imageId,
                                         @AuthenticationPrincipal JwtAuthenticatedUser user) throws IOException {
        Long userId = user.userId();
        s3Service.deleteImage(imageId,userId);
        return ResponseEntity.ok("사진을 삭제했습니다.");
    }

    @PostMapping("/pictures/{id}/report")
    public ResponseEntity<String> report(@PathVariable("id") Long imageId,
                                         @Valid @RequestBody PictureReportRequest request,
                                         @AuthenticationPrincipal JwtAuthenticatedUser user) {
        pictureReportService.report(imageId, user.userId(), user.username(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body("사진 신고를 등록했습니다.");
    }
}
