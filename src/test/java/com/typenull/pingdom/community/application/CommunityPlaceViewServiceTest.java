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

    @Test
    void 게시글에서_연결된_장소로_처음_이동하면_조회수를_증가한다() {
        PlaceDetailResponse response = mock(PlaceDetailResponse.class);
        givenLinkedPlace();
        when(dailyViewRepository.insertIgnoreDuplicate(anyLong(), anyLong(), any())).thenReturn(1);
        when(placeQueryService.getPlace(20L)).thenReturn(response);

        assertThat(service.record(10L, 20L, 7L)).isSameAs(response);

        verify(mapPlaceRepository).increaseCommunityViewCount(20L);
        verify(dailyViewRepository).insertIgnoreDuplicate(eq(7L), eq(20L), eq(java.time.LocalDate.of(2026, 9, 13)));
        verify(placeQueryService).getPlace(20L);
    }

    @Test
    void 같은_날_재진입은_조회수를_증가하지_않는다() {
        givenLinkedPlace();
        when(dailyViewRepository.insertIgnoreDuplicate(anyLong(), anyLong(), any())).thenReturn(0);

        service.record(10L, 20L, 7L);

        verify(mapPlaceRepository, never()).increaseCommunityViewCount(anyLong());
    }

    @Test
    void 연결되지_않은_장소는_조회_기록을_남기지_않는다() {
        when(postRepository.existsByIdAndHiddenFalse(10L)).thenReturn(true);
        when(postPlaceRepository.existsByCommunityPost_IdAndMapPlace_Id(10L, 20L)).thenReturn(false);

        assertThatThrownBy(() -> service.record(10L, 20L, 7L)).isInstanceOf(CommunityException.class);

        verifyNoInteractions(dailyViewRepository, mapPlaceRepository, placeQueryService);
    }

    private void givenLinkedPlace() {
        when(postRepository.existsByIdAndHiddenFalse(10L)).thenReturn(true);
        when(postPlaceRepository.existsByCommunityPost_IdAndMapPlace_Id(10L, 20L)).thenReturn(true);
        when(mapPlaceRepository.existsByIdAndOperatingStatusAndDiscoveryStatus(anyLong(), any(), any()))
                .thenReturn(true);
    }
}
