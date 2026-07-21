package com.typenull.pingdom.place.application.service.recommendation.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.application.service.recommendation.feature.PlaceRecommendationFeatureLogService;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotService;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationVersionSnapshotService;
import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationClick;
import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationConversion;
import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationConversionType;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationClickRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationConversionRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceRecommendationConversionServiceTest {

    @Mock
    private PlaceRecommendationClickRepository placeRecommendationClickRepository;

    @Mock
    private PlaceRecommendationConversionRepository placeRecommendationConversionRepository;

    @Mock
    private PlaceRecommendationFeatureLogService placeRecommendationFeatureLogService;

    @Mock
    private PlaceRecommendationSnapshotService placeRecommendationSnapshotService;

    @Mock
    private PlaceRecommendationVersionSnapshotService placeRecommendationVersionSnapshotService;

    @InjectMocks
    private PlaceRecommendationConversionService placeRecommendationConversionService;

    @Test
    void recordConversionLinksFeatureLogFromAttributedClick() {
        Long userId = 10L;
        Long placeId = 20L;
        PlaceRecommendationClick click = PlaceRecommendationClick.builder()
                .id(30L)
                .userId(userId)
                .placeId(placeId)
                .requestId("recommendation-request")
                .recommendationVersion("place-rec-v2")
                .build();
        when(placeRecommendationConversionRepository.existsByUserIdAndPlaceIdAndConversionType(
                userId,
                placeId,
                PlaceRecommendationConversionType.BOOKMARK
        )).thenReturn(false);
        when(placeRecommendationClickRepository
                .findFirstByUserIdAndPlaceIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        any(), any(), any()
                )).thenReturn(Optional.of(click));
        when(placeRecommendationFeatureLogService.findFeatureLogId(
                "recommendation-request",
                userId,
                placeId,
                "place-rec-v2"
        ))
                .thenReturn(40L);

        placeRecommendationConversionService.recordConversionIfEligible(
                userId,
                placeId,
                PlaceRecommendationConversionType.BOOKMARK
        );

        ArgumentCaptor<PlaceRecommendationConversion> captor =
                ArgumentCaptor.forClass(PlaceRecommendationConversion.class);
        verify(placeRecommendationConversionRepository).save(captor.capture());
        PlaceRecommendationConversion conversion = captor.getValue();
        assertThat(conversion.getPlaceRecommendationClickId()).isEqualTo(30L);
        assertThat(conversion.getPlaceRecommendationFeatureLogId()).isEqualTo(40L);
        assertThat(conversion.getRecommendationVersion()).isEqualTo("place-rec-v2");
        verify(placeRecommendationSnapshotService).increaseConversionCount(
                placeId,
                PlaceRecommendationConversionType.BOOKMARK
        );
        verify(placeRecommendationVersionSnapshotService).increaseConversionCount(
                placeId,
                "place-rec-v2",
                PlaceRecommendationConversionType.BOOKMARK
        );
    }

    @Test
    void recordConversionContinuesWhenAttributedFeatureLogDoesNotExist() {
        Long userId = 11L;
        Long placeId = 21L;
        PlaceRecommendationClick click = PlaceRecommendationClick.builder()
                .id(31L)
                .userId(userId)
                .placeId(placeId)
                .requestId("logging-disabled-request")
                .recommendationVersion("place-rec-v1")
                .build();
        when(placeRecommendationConversionRepository.existsByUserIdAndPlaceIdAndConversionType(
                userId,
                placeId,
                PlaceRecommendationConversionType.LIKE
        )).thenReturn(false);
        when(placeRecommendationClickRepository
                .findFirstByUserIdAndPlaceIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        any(), any(), any()
                )).thenReturn(Optional.of(click));
        when(placeRecommendationFeatureLogService.findFeatureLogId(
                "logging-disabled-request",
                userId,
                placeId,
                "place-rec-v1"
        ))
                .thenReturn(null);

        placeRecommendationConversionService.recordConversionIfEligible(
                userId,
                placeId,
                PlaceRecommendationConversionType.LIKE
        );

        ArgumentCaptor<PlaceRecommendationConversion> captor =
                ArgumentCaptor.forClass(PlaceRecommendationConversion.class);
        verify(placeRecommendationConversionRepository).save(captor.capture());
        assertThat(captor.getValue().getPlaceRecommendationFeatureLogId()).isNull();
        verify(placeRecommendationSnapshotService).increaseConversionCount(
                placeId,
                PlaceRecommendationConversionType.LIKE
        );
    }
}
