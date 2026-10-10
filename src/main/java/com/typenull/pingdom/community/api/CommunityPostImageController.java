package com.typenull.pingdom.community.api;
import com.typenull.pingdom.community.api.dto.CommunityImageUploadResponse;
import com.typenull.pingdom.community.application.CommunityPostImageService;
import com.typenull.pingdom.shared.config.swagger.*;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import com.typenull.pingdom.identity.domain.exception.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;
@RestController @RequestMapping("/community/images") @RequiredArgsConstructor
@ApiAudience(ApiAudience.Group.APP)
@io.swagger.v3.oas.annotations.tags.Tag(name = SwaggerTagCatalog.COMMUNITY_POST)
@SecurityRequirement(name = "bearerAuth")
public class CommunityPostImageController {
    private final CommunityPostImageService service;
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "게시글 이미지 업로드", description = "JPEG/PNG 10MiB 이하. 검증 후 메타데이터를 제거해 저장하며 최대 10개의 본인 imageId를 게시글에 입력 순서대로 연결합니다. 미사용 이미지는 24시간 후 삭제 Outbox로 정리합니다.")
    @ApiResponses({@ApiResponse(responseCode="201",description="이미지 ID·URL 및 만료 시각"),
        @ApiResponse(responseCode="400",description="IMAGE_FILE_INVALID: 빈 파일·크기·실제 형식 오류"),
        @ApiResponse(responseCode="503",description="IMAGE_UPLOAD_UNAVAILABLE: 저장소 사용 불가")})
    public ResponseEntity<CommunityImageUploadResponse> upload(@RequestPart("file") MultipartFile file,
            @CurrentUser JwtAuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(userId(user), file));
    }
    @DeleteMapping("/{imageId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "본인 게시글 이미지 또는 미사용 이미지 삭제")
    @ApiResponse(responseCode="404",description="IMAGE_NOT_FOUND: 없는 이미지·타인 이미지·숨김 게시글")
    public void delete(@PathVariable long imageId, @CurrentUser JwtAuthenticatedUser user) {
        service.delete(userId(user), imageId);
    }
    private long userId(JwtAuthenticatedUser user) {
        return JwtAuthenticatedUser.require(user, () -> new AuthException(AuthErrorCode.INVALID_TOKEN)).userId();
    }
}
