package com.typenull.pingdom.moderation.api.place.lookup;

import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapRequest;
import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapResponse;
import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapResponse.GroupPage;
import com.typenull.pingdom.moderation.application.query.place.map.AdminPlaceMapQueryService;
import com.typenull.pingdom.shared.config.swagger.ApiAudience;
import com.typenull.pingdom.shared.config.swagger.SwaggerTagCatalog;
import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/admin/places/map", produces = "application/json")
@RequiredArgsConstructor
@AdminOnly
@ApiAudience(ApiAudience.Group.ADMIN)
@Tag(name = SwaggerTagCatalog.PLACE_LOOKUP)
public class AdminPlaceMapController {
    private final AdminPlaceMapQueryService service;

    @GetMapping
    @Operation(summary = "관리자 지도 영역 전체 마커 및 확대 필요 상태 조회", description = "v2: 동일 경계·검색·필터의 유효 좌표 장소를 집계합니다. "
            + "zoom·CSS 크기와 무관하게 0~displayLimit개면 MARKERS와 전체 개별 마커를, 초과하면 ZOOM_REQUIRED와 빈 마커 배열을 반환합니다. "
            + "displayLimit 초기값은 100이며 클라이언트는 상향할 수 없습니다. viewportPlaceCount는 정확한 현재 영역 전체 수입니다. "
            + "0건도 MARKERS, complete=true입니다. 초과는 HTTP 200, complete=false, RESULT_BUDGET_EXCEEDED, ZOOM_IN입니다. "
            + "mode는 호환상 SPATIAL, regions/clusters는 항상 빈 배열입니다. 그룹·대표 장소는 생성하지 않습니다. "
            + "같은 좌표의 서로 다른 ID를 합치지 않으며 목록 page/limit/sortParam과 독립적입니다. "
            + "최대 확대에서도 초과하면 기존 장소 목록을 사용합니다. 인증·입력 검증·no-store·3초 SQL timeout은 유지합니다.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "전체 마커, 정상 0건 또는 표시 한도 초과에 따른 확대 안내",
            content = @Content(schema = @Schema(implementation = AdminPlaceMapResponse.class), examples = {
                @ExampleObject(name = "markers", summary = "축소 상태에서도 영역 내 모든 개별 장소 표시", value = """
                    {"mode":"SPATIAL","zoom":0,"regions":[],"clusters":[],
                     "markers":[{"id":1820,"name":"예시 카페","latitude":35.875,"longitude":128.610,
                       "category":"CAFE","discoveryStatus":"HIDDEN","operatingStatus":"OPERATING"}],
                     "viewportPlaceCount":1,"complete":true,"incompleteReason":null,"recoveryAction":null,
                     "displayLimit":100,"displayStatus":"MARKERS"}
                    """),
                @ExampleObject(name = "zoomRequired", summary = "101개 이상은 최대 확대에서도 마커 없이 확대 안내", value = """
                    {"mode":"SPATIAL","zoom":20,"regions":[],"clusters":[],"markers":[],
                     "viewportPlaceCount":101,"complete":false,
                     "incompleteReason":"RESULT_BUDGET_EXCEEDED","recoveryAction":"ZOOM_IN",
                     "displayLimit":100,"displayStatus":"ZOOM_REQUIRED"}
                    """),
                @ExampleObject(name = "empty", summary = "정상 0건", value = """
                    {"mode":"SPATIAL","zoom":13,"regions":[],"clusters":[],"markers":[],
                     "viewportPlaceCount":0,"complete":true,"incompleteReason":null,"recoveryAction":null,
                     "displayLimit":100,"displayStatus":"MARKERS"}
                    """)
            })),
            @ApiResponse(responseCode = "400", description = "잘못된 경계·zoom·크기·필터",
            content = @Content(examples = @ExampleObject(value = """
                {"message":"관리자 지도 조회 조건이 올바르지 않습니다.","code":"ADMIN_PLACE_MAP_INVALID_REQUEST"}
                """))),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 필요"),
            @ApiResponse(responseCode = "503", description = "ADMIN_PLACE_MAP_QUERY_TIMEOUT")})
    public ResponseEntity<AdminPlaceMapResponse> find(@Valid @ModelAttribute @ParameterObject AdminPlaceMapRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.find(request));
    }

    @GetMapping("/clusters/{clusterId}/places")
    @Operation(summary = "관리자 지도 그룹 내부 장소 조회", description = "v1에서 발급한 기존 토큰의 호환 조회이며 v2 지도 흐름에서는 호출하지 않습니다. "
            + "v2 지도 조회는 새 그룹 토큰을 발급하지 않습니다. 원래 필터·영역·격자를 유지한 ID 오름차순 페이지. "
            + "page 1~1000000, limit 1~100. 토큰은 5분 유효하며 변조·만료·그룹 변경·정책 버전 변경은 409. "
            + "409 발생 시 지도 재조회 후 그룹을 다시 선택합니다. 각 요청에서 관리자 권한을 재검사합니다.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "그룹 내부 장소 페이지"),
            @ApiResponse(responseCode = "400", description = "잘못된 페이지"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 필요"),
            @ApiResponse(responseCode = "409", description = "ADMIN_PLACE_MAP_CLUSTER_INVALID: 지도 재조회 필요",
            content = @Content(examples = @ExampleObject(value = """
                {"message":"지도 그룹이 만료되었거나 변경되었습니다. 지도를 다시 조회해주세요.","code":"ADMIN_PLACE_MAP_CLUSTER_INVALID"}
                """))),
            @ApiResponse(responseCode = "503", description = "ADMIN_PLACE_MAP_QUERY_TIMEOUT")})
    public ResponseEntity<GroupPage> groupPlaces(@PathVariable String clusterId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.groupPlaces(clusterId, page, limit));
    }
}
