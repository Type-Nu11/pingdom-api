package com.typenull.pingdom.community.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.community.domain.exception.CommunityException;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPlaceDailyViewRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostPlaceRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import com.typenull.pingdom.place.api.dto.place.detail.PlaceDetailResponse;
import com.typenull.pingdom.place.application.service.place.PlaceQueryService;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CommunityPlaceViewServiceTest {
    private final CommunityPostRepository postRepository = mock(CommunityPostRepository.class);
    private final CommunityPostPlaceRepository postPlaceRepository = mock(CommunityPostPlaceRepository.class);
    private final CommunityPlaceDailyViewRepository dailyViewRepository = mock(CommunityPlaceDailyViewRepository.class);
    private final MapPlaceRepository mapPlaceRepository = mock(MapPlaceRepository.class);
    private final PlaceQueryService placeQueryService = mock(PlaceQueryService.class);
    private final CommunityPlaceViewService service = new CommunityPlaceViewService(
            postRepository,
            postPlaceRepository,
            dailyViewRepository,
            mapPlaceRepository,
            placeQueryService,
            Clock.fixed(Instant.parse("2026-09-12T15:00:00Z"), ZoneOffset.UTC)
    );

    /**
     * 게시글에 연결된 공개 장소의 일일 조회 기록이 새로 삽입되면 장소 조회수를 증가시키고 상세 응답을 그대로 반환하는지 검증.
     * UTC 15시가 한국 날짜의 다음 날로 기록되는지도 확인.
     */
    @Test
    void countsFirstLinkedPlaceView() {
        PlaceDetailResponse response = mock(PlaceDetailResponse.class);
        givenLinkedPlace();
        when(dailyViewRepository.insertIgnoreDuplicate(anyLong(), anyLong(), any())).thenReturn(1);
        when(placeQueryService.getPlace(20L)).thenReturn(response);

        assertThat(service.record(10L, 20L, 7L)).isSameAs(response);

        verify(mapPlaceRepository).increaseCommunityViewCount(20L);
        verify(dailyViewRepository).insertIgnoreDuplicate(eq(7L), eq(20L), eq(java.time.LocalDate.of(2026, 9, 13)));
        verify(placeQueryService).getPlace(20L);
    }

    /**
     * 이미 같은 날의 조회 기록이 있어 삽입 결과가 0이면 장소 조회수를 다시 증가시키지 않는지 검증.
     */
    @Test
    void skipsDuplicateDailyViewCount() {
        givenLinkedPlace();
        when(dailyViewRepository.insertIgnoreDuplicate(anyLong(), anyLong(), any())).thenReturn(0);

        service.record(10L, 20L, 7L);

        verify(mapPlaceRepository, never()).increaseCommunityViewCount(anyLong());
    }

    /**
     * 게시글과 연결되지 않은 장소로 이동하면 예외를 반환하고 일일 기록·장소 집계·상세 조회를 호출하지 않는지 검증.
     */
    @Test
    void rejectsUnlinkedPlaceView() {
        when(postRepository.existsByIdAndHiddenFalse(10L)).thenReturn(true);
        when(postPlaceRepository.existsByCommunityPost_IdAndMapPlace_Id(10L, 20L)).thenReturn(false);

        assertThatThrownBy(() -> service.record(10L, 20L, 7L)).isInstanceOf(CommunityException.class);

        verifyNoInteractions(dailyViewRepository, mapPlaceRepository, placeQueryService);
    }

    /**
     * 숨겨지지 않은 게시글에 공개 영업 장소가 연결된 정상 조회 조건을 설정.
     */
    private void givenLinkedPlace() {
        when(postRepository.existsByIdAndHiddenFalse(10L)).thenReturn(true);
        when(postPlaceRepository.existsByCommunityPost_IdAndMapPlace_Id(10L, 20L)).thenReturn(true);
        when(mapPlaceRepository.existsByIdAndOperatingStatusAndDiscoveryStatus(anyLong(), any(), any()))
                .thenReturn(true);
    }
}
