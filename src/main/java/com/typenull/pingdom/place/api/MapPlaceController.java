package com.typenull.pingdom.place.api;

import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.place.application.service.place.MapPlaceService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/places")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class MapPlaceController {

    private final MapPlaceService mapPlaceService;

    @Hidden
    @DeleteMapping("/{id:\\d+}")
    @Operation(summary = "장소 삭제", description = "지정한 장소 ID를 삭제합니다. 본인 소유 장소만 삭제 가능합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "장소 삭제 성공",
                    content = @Content(
                            examples = @ExampleObject(value = "\"장소를 삭제했습니다.\"")
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
                    description = "본인 소유가 아닌 장소 삭제 시도",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "자신의 장소만 삭제할 수 있습니다.",
                                              "code": "OTHERS_PLACE_NOT_DELETED"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "장소를 찾을 수 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "장소를 찾을 수 없습니다.",
                                              "code": "PLACE_NOT_FOUND"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<String> delete(
            @Parameter(description = "삭제할 장소 ID", example = "1") @PathVariable("id") Long placeId,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        mapPlaceService.deletePlace(placeId, user.userId());
        return ResponseEntity.ok("장소를 삭제했습니다.");
    }
}
