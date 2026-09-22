package com.typenull.pingdom.place.application.service.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationApplicationRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlaceRegistrationMediaBackfillServiceTest {

    private final PlaceRegistrationApplicationRepository applicationRepository = org.mockito.Mockito.mock(
            PlaceRegistrationApplicationRepository.class);
    private final MapPlaceRepository placeRepository = org.mockito.Mockito.mock(MapPlaceRepository.class);
    private final PlaceRegistrationMediaPromotionService promotionService = org.mockito.Mockito.mock(
            PlaceRegistrationMediaPromotionService.class);
    private final PlaceRegistrationMediaBackfillService service = new PlaceRegistrationMediaBackfillService(
            applicationRepository,
            placeRepository,
            promotionService
    );

    /**
     * 승격 서비스가 신규 0건·기존 2건을 반환하면 백필 결과가 미처리로 표시되고 건수가 유지되는지 확인합니다.
     */
    @Test
    void skipsAlreadyPromotedMedia() {
        PlaceRegistrationApplication application = org.mockito.Mockito.mock(PlaceRegistrationApplication.class);
        MapPlace place = org.mockito.Mockito.mock(MapPlace.class);
        when(application.getStatus()).thenReturn(PlaceRegistrationStatus.COMPLETED);
        when(application.getCompletedPlaceId()).thenReturn(70069L);
        when(applicationRepository.findByIdForUpdate(77L)).thenReturn(Optional.of(application));
        when(placeRepository.findByIdForUpdate(70069L)).thenReturn(Optional.of(place));
        when(promotionService.promote(place, application)).thenReturn(
                new PlaceRegistrationMediaPromotionService.PromotionResult(0, 2)
        );

        var result = service.backfill(77L);

        assertThat(result.processed()).isFalse();
        assertThat(result.promotedMediaCount()).isZero();
        assertThat(result.alreadyPromotedMediaCount()).isEqualTo(2);
        verify(promotionService).promote(place, application);
    }
}
