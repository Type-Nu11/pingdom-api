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
    @Operation(summary = "관리자 지도 지역 집계 및 공간 그룹 조회", description = "zoom 0~8 SIDO, 9~11 SIGUNGU, 12~20 SPATIAL. "
            + "지역은 화면 안 장소 기준으로 선택하며 count는 동일 필터의 지역 전체 수입니다. "
            + "bounds는 행정 경계가 아닌 PLACE_EXTENT입니다. 지역 미연결 장소는 공간 그룹/마커로 반환합니다. "
            + "viewportPlaceCount와 지역 count는 합산하지 않습니다. 초기 상한 200개(설정 가능)이며 초과 시 재집계합니다. EXPAND는 nextZoom 이상 확대합니다. clusterKey는 표시용 고정 키입니다. "
            + "안전 한도 초과는 complete=false, RESULT_BUDGET_EXCEEDED, ZOOM_IN으로 안내합니다. "
            + "정상 0건은 빈 배열, viewportPlaceCount=0, complete=true입니다.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "지역/공간 혼합 또는 빈 결과",
            content = @Content(schema = @Schema(implementation = AdminPlaceMapResponse.class), examples = {
                @ExampleObject(name = "spatial", summary = "공간 그룹과 개별 장소", value = """
                    {"mode":"SPATIAL","zoom":13,"regions":[],
                     "clusters":[{"clusterKey":"v1:filterHash:resolution:x:y","clusterId":"opaque-token","count":24,"latitude":35.871,"longitude":128.601,
                       "bounds":{"west":128.600,"south":35.870,"east":128.603,"north":35.873},
                       "countScope":"VIEWPORT","action":"EXPAND","nextZoom":14}],
                     "markers":[{"id":1820,"name":"예시 카페","latitude":35.875,"longitude":128.610,
                       "category":"CAFE","discoveryStatus":"HIDDEN","operatingStatus":"OPERATING"}],
                     "viewportPlaceCount":25,"complete":true,"incompleteReason":null,"recoveryAction":null}
                    """),
                @ExampleObject(name = "regional", summary = "지역 전체 수와 화면 내 수 구분", value = """
                    {"mode":"SIGUNGU","zoom":10,
                     "regions":[{"regionLevel":"SIGUNGU","regionCode":"27260","name":"대구광역시 수성구",
                       "count":320,"countScope":"REGION","latitude":35.858,"longitude":128.631,
                       "bounds":{"west":128.60,"south":35.80,"east":128.76,"north":35.90},
                       "boundsSource":"PLACE_EXTENT","action":"EXPAND","nextZoom":12}],
                     "clusters":[],"markers":[],"viewportPlaceCount":120,"complete":true,
                     "incompleteReason":null,"recoveryAction":null}
                    """),
                @ExampleObject(name = "empty", summary = "정상 0건", value = """
                    {"mode":"SPATIAL","zoom":13,"regions":[],"clusters":[],"markers":[],
                     "viewportPlaceCount":0,"complete":true,"incompleteReason":null,"recoveryAction":null}
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
    @Operation(summary = "관리자 지도 그룹 내부 장소 조회", description = "원래 필터·영역·격자를 유지한 ID 오름차순 페이지. "
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
