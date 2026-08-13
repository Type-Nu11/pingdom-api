package com.typenull.pingdom.moderation.api.place.lookup;

import com.typenull.pingdom.moderation.api.dto.place.query.AdminMapPlaceDetailResponse;
import com.typenull.pingdom.moderation.api.dto.place.query.AdminMapPlaceResponse;
import com.typenull.pingdom.moderation.application.query.place.lookup.AdminMapPlaceLookupQueryService;
import com.typenull.pingdom.moderation.domain.AdminPlaceSortParam;
import com.typenull.pingdom.moderation.domain.SortParam;
import com.typenull.pingdom.place.domain.place.category.PlaceCategoryPolicy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/places")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminPlaceLookupController {

    private final AdminMapPlaceLookupQueryService adminMapPlaceLookupQueryService;

    @GetMapping
    @Operation(
            summary = "관리자 장소 목록 조회",
            description = "관리자가 등록된 장소 목록을 페이지 단위로 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "장소 목록 조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminMapPlaceResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "categoryFilteredResult",
                                            summary = "카테고리 필터 조회 결과",
                                            value = """
                                            {
                                              "places": [
                                                {
                                                  "id": 1,
                                                  "name": "진주성",
                                                  "address": "경상남도 진주시 남강로 626",
                                                  "discoveryStatus": "VISIBLE",
                                                  "category": "관광",
                                                  "categoryName": "관광",
                                                  "touristCategories": ["EXHIBITION"],
                                                  "latitude": 35.1894,
                                                  "longitude": 128.0789,
                                                  "userId": 3,
                                                  "registrant": "placeRegistrar",
                                                  "placeGrowth": {
                                                    "photoCount": 10,
                                                    "level": 5,
                                                    "currentLevelMinPhotoCount": 10,
                                                    "nextLevelMinPhotoCount": 16,
                                                    "progressPercent": 0
                                                  }
                                                }
                                              ],
                                              "page": 1,
                                              "limit": 20,
                                              "totalCount": 1,
                                              "totalPages": 1,
                                              "hasNext": false
                                            }
                                            """
                                    ),
                                    @ExampleObject(
                                            name = "emptyResult",
                                            summary = "카테고리 필터 결과 없음",
                                            value = """
                                            {
                                              "places": [],
                                              "page": 1,
                                              "limit": 20,
                                              "totalCount": 0,
                                              "totalPages": 1,
                                              "hasNext": false
                                            }
                                            """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "지원하지 않는 장소 정렬 기준",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "장소 목록은 LATEST 또는 OLDEST 정렬만 지원합니다.",
                                              "code": "UNSUPPORTED_PLACE_SORT_PARAM"
                                            }
                                            """
                            )
                    )
            )
    })
    public AdminMapPlaceResponse listPlaces(
            @Parameter(description = "조회할 페이지 번호. 1 이상으로 보정됩니다.", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "조회할 최대 개수. 1~100 범위로 보정됩니다.", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(
                    description = "장소 정렬 기준. LATEST, OLDEST, LEVEL_DESC만 지원합니다.",
                    example = "LATEST",
                    schema = @Schema(type = "string", allowableValues = {"LATEST", "OLDEST", "LEVEL_DESC"})
            )
            @RequestParam(defaultValue = "LATEST") String sortParam,
            @Parameter(description = "장소 검색 키워드. 장소명, 등록자 ID, 주소로 검색합니다.", example = "용인")
            @RequestParam(required = false, defaultValue = "") String keyword,
            @Parameter(
                    description = "일반 장소 카테고리 필터. 입력값을 표준 카테고리로 정규화한 뒤 "
                            + "AdminMapPlaceItem.category와 정확히 비교합니다. touristCategories와는 별도 기준입니다.",
                    example = "카페",
                    schema = @Schema(
                            type = "string",
                            allowableValues = {
                                    PlaceCategoryPolicy.CAFE,
                                    PlaceCategoryPolicy.RESTAURANT,
                                    PlaceCategoryPolicy.TOURISM,
                                    PlaceCategoryPolicy.SCENERY,
                                    PlaceCategoryPolicy.CULTURE,
                                    PlaceCategoryPolicy.SHOPPING,
                                    PlaceCategoryPolicy.ACCOMMODATION,
                                    PlaceCategoryPolicy.EXPERIENCE
                            }
                    )
            )
            @RequestParam(required = false) String category
    ) {
        return adminMapPlaceLookupQueryService.listPlaces(
                page,
                limit,
                AdminPlaceSortParam.from(sortParam),
                keyword,
                category
        );
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "관리자 장소 상세 조회",
            description = "관리자가 특정 장소의 기본 정보와 연결된 게시글 목록을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "장소 상세 조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminMapPlaceDetailResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "id": 1,
                                              "name": "진주성",
                                              "address": "경상남도 진주시 남강로 626",
                                              "discoveryStatus": "VISIBLE",
                                              "category": "관광",
                                              "categoryName": "관광",
                                              "latitude": 35.1894,
                                              "longitude": 128.0789,
                                              "userId": 3,
                                              "username": "placeOwner",
                                              "sortParam": "LATEST",
                                              "postCount": 1,
                                              "placeGrowth": {
                                                "photoCount": 10,
                                                "level": 5,
                                                "currentLevelMinPhotoCount": 10,
                                                "nextLevelMinPhotoCount": 16,
                                                "progressPercent": 0
                                              },
                                              "posts": [
                                                {
                                                  "id": 10,
                                                  "imageUrl": "https://example.com/image.jpg",
                                                  "title": "야경 사진",
                                                  "description": "남강 야경입니다.",
                                                  "userId": 3,
                                                  "username": "pingdom_user",
                                                  "createdAt": "2026-05-28T12:00:00",
                                                  "likeCount": 5,
                                                  "visibilityStatus": "HIDDEN",
                                                  "hiddenReason": "ADMIN_HIDDEN"
                                                }
                                              ]
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
    public AdminMapPlaceDetailResponse getPlace(
            @Parameter(description = "조회할 장소 ID", example = "1") @PathVariable Long id,
            @Parameter(description = "게시글 정렬 기준", example = "LATEST")
            @RequestParam(defaultValue = "LATEST") SortParam sortParam,
            @Parameter(description = "게시글 검색", example = "용인")
            @RequestParam(required = false, defaultValue = "") String keyword
    ) {
        return adminMapPlaceLookupQueryService.getPlace(id, sortParam, keyword);
    }
}
