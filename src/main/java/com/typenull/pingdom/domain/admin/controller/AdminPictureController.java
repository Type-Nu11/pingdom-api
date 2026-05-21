package com.typenull.pingdom.domain.admin.controller;

import com.typenull.pingdom.domain.admin.dto.picture.AdminPictureResponse;
import com.typenull.pingdom.domain.admin.service.AdminPictureQueryService;
import com.typenull.pingdom.domain.admin.service.AdminPictureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminPictureController {

    private final AdminPictureService adminPictureService;
    private final AdminPictureQueryService adminPictureQueryService;

    @GetMapping("/pictures")
    @Operation(
            summary = "관리자 사진 목록 조회",
            description = "관리자가 최근 사진 목록을 조회합니다. limit 값은 내부적으로 1~100 범위로 보정됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "사진 목록 조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AdminPictureResponse.class)))
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
    public List<AdminPictureResponse> listPictures(
            @Parameter(description = "조회할 최대 개수. 1~100 범위로 보정됩니다.", example = "20")
            @RequestParam(defaultValue = "20") int limit
    ) {
        return adminPictureQueryService.listPictures(limit);
    }

    @DeleteMapping("/pictures/{id}/delete")
    @Operation(
            summary = "관리자 사진 삭제",
            description = "관리자가 사진을 강제로 삭제합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "사진 삭제 성공"
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
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "사진 삭제 처리 실패",
                    content = @Content(
                            examples = {
                                    @ExampleObject(
                                            name = "delete-failed",
                                            value = """
                                                    {
                                                      "message": "사진 삭제에 실패했습니다.",
                                                      "code": "PICTURE_DELETE_FAILED"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "s3-connection-error",
                                            value = """
                                                    {
                                                      "message": "S3 연결에 실패했습니다.",
                                                      "code": "S3_CONNECTION_ERROR"
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    })
    public ResponseEntity<Void> deletePicture(
            @Parameter(description = "삭제할 사진 ID", example = "10") @PathVariable Long id
    ) {
        adminPictureService.deletePicture(id);
        return ResponseEntity.noContent().build();
    }
}
