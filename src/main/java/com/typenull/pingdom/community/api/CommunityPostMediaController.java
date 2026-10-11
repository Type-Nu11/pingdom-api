package com.typenull.pingdom.community.api;

import com.typenull.pingdom.community.api.dto.CommunityPostMediaUploadResponse;
import com.typenull.pingdom.community.application.CommunityPostMediaService;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.config.swagger.ApiAudience;
import com.typenull.pingdom.shared.config.swagger.SwaggerTagCatalog;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/community/images")
@RequiredArgsConstructor
@ApiAudience(ApiAudience.Group.APP)
@Tag(name = SwaggerTagCatalog.COMMUNITY_POST)
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "잘못된 JPEG/PNG, 10MiB 초과 또는 해상도 제한 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "다른 사용자의 사진", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "사진 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "이미 연결된 사진", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "413", description = "multipart 크기 제한 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "저장소 사용 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class CommunityPostMediaController {
    private final CommunityPostMediaService mediaService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "게시글 사진 임시 업로드", description = "file 한 장씩 업로드합니다. JPEG/PNG, 10MiB 이하이며 결과 imageId를 작성 요청 imageIds에 넣습니다. 미연결 사진은 24시간 후 정리됩니다.")
    @ApiResponse(responseCode = "201", description = "임시 사진 저장", useReturnTypeSchema = true)
    public ResponseEntity<CommunityPostMediaUploadResponse> upload(
            @RequestPart("file") MultipartFile file, @CurrentUser JwtAuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mediaService.upload(userId(user), file));
    }

    @DeleteMapping("/{imageId}")
    @Operation(summary = "게시글 미연결 사진 취소", description = "본인의 미연결 사진만 취소할 수 있습니다. S3 삭제는 비동기로 재시도합니다.")
    @ApiResponse(responseCode = "204", description = "사진 취소")
    public ResponseEntity<Void> cancel(@PathVariable long imageId, @CurrentUser JwtAuthenticatedUser user) {
        mediaService.cancel(userId(user), imageId);
        return ResponseEntity.noContent().build();
    }

    private long userId(JwtAuthenticatedUser user) {
        return JwtAuthenticatedUser.require(user, () -> new AuthException(AuthErrorCode.INVALID_TOKEN)).userId();
    }
}
