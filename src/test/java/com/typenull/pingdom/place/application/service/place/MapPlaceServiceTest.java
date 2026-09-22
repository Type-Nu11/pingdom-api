package com.typenull.pingdom.place.application.service.place;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotService;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkTrendEventRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class MapPlaceServiceTest {

    private final MapPlaceRepository mapPlaceRepository = mock(MapPlaceRepository.class);
    private final MerchantOwnerPlaceRepository merchantOwnerPlaceRepository = mock(MerchantOwnerPlaceRepository.class);
    private final PlaceRecommendationSnapshotService placeRecommendationSnapshotService =
            mock(PlaceRecommendationSnapshotService.class);
    private final MapBookmarkRepository mapBookmarkRepository = mock(MapBookmarkRepository.class);
    private final MapBookmarkTrendEventRepository mapBookmarkTrendEventRepository =
            mock(MapBookmarkTrendEventRepository.class);
    private MapPlaceService mapPlaceService;

    /**
     * 장소·소유권·북마크·추천 집계 저장소의 호출 순서를 모의로 관찰할 서비스를 준비합니다.
     */
    @BeforeEach
    void setUp() {
        mapPlaceService = new MapPlaceService(
                mapPlaceRepository,
                merchantOwnerPlaceRepository,
                placeRecommendationSnapshotService,
                mapBookmarkRepository,
                mapBookmarkTrendEventRepository
        );
    }

    /**
     * 소유자의 삭제에서 북마크 추세·북마크·장소·추천 스냅샷 순으로 정리하는지 확인합니다.
     */
    @Test
    void deletesBookmarkBeforeOwnedPlace() {
        MapPlace place = MapPlace.builder()
                .id(10L)
                .name("즐겨찾기 연결 장소")
                .address("서울시 중구 테스트로 1")
                .latitude(37.5665)
                .longitude(126.9780)
                .registrant("merchant")
                .build();
        when(mapPlaceRepository.findById(10L)).thenReturn(Optional.of(place));
        when(merchantOwnerPlaceRepository.existsByPlaceIdAndMerchantOwnerUserId(10L, 7L)).thenReturn(true);

        mapPlaceService.deletePlace(10L, 7L);

        InOrder inOrder = inOrder(
                mapBookmarkTrendEventRepository,
                mapBookmarkRepository,
                mapPlaceRepository,
                placeRecommendationSnapshotService
        );
        inOrder.verify(mapBookmarkTrendEventRepository).deleteAllByPlaceId(10L);
        inOrder.verify(mapBookmarkRepository).deleteAllByPlaceId(10L);
        inOrder.verify(mapPlaceRepository).delete(place);
        inOrder.verify(placeRecommendationSnapshotService).delete(10L);
    }

    /**
     * 소유권이 없으면 전용 오류로 거절하고 북마크·장소 삭제를 호출하지 않는지 확인합니다.
     */
    @Test
    void preservesPlaceForNonOwner() {
        MapPlace place = MapPlace.builder()
                .id(10L)
                .name("다른 사장님 장소")
                .address("서울시 중구 테스트로 2")
                .latitude(37.5665)
                .longitude(126.9780)
                .registrant("merchant")
                .build();
        when(mapPlaceRepository.findById(10L)).thenReturn(Optional.of(place));
        when(merchantOwnerPlaceRepository.existsByPlaceIdAndMerchantOwnerUserId(10L, 7L)).thenReturn(false);

        assertThatThrownBy(() -> mapPlaceService.deletePlace(10L, 7L))
                .isInstanceOfSatisfying(MapException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(MapErrorCode.OTHERS_PLACE_NOT_DELETED));

        verify(mapBookmarkTrendEventRepository, never()).deleteAllByPlaceId(10L);
        verify(mapBookmarkRepository, never()).deleteAllByPlaceId(10L);
        verify(mapPlaceRepository, never()).delete(place);
    }
}
