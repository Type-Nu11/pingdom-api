package com.typenull.pingdom.place.application.service.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.place.api.dto.review.PlaceReviewSummaryResponse.ReasonCount;
import com.typenull.pingdom.place.domain.review.PlaceReviewRecommendReason;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceReviewSummaryQueryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceReviewSummaryQueryRepository.SummaryCounts;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlaceReviewSummaryServiceTest {

    private final PlaceReviewSummaryQueryRepository repository = mock(PlaceReviewSummaryQueryRepository.class);
    private final PlaceReviewSummaryService service = new PlaceReviewSummaryService(repository);

    @Test
    void returnsAllSevenReasonsInDeclaredOrderAndSeparateCounts() {
        when(repository.findByPlaceId(17L)).thenReturn(Optional.of(counts(4, 2)));

        var result = service.summary(17L);

        assertThat(result.placeId()).isEqualTo(17);
        assertThat(result.totalReviewCount()).isEqualTo(4);
        assertThat(result.participantCount()).isEqualTo(2);
        assertThat(result.reasonCounts()).extracting(ReasonCount::reason)
                .containsExactly(PlaceReviewRecommendReason.values());
        assertThat(result.reasonCounts()).extracting(ReasonCount::count).containsOnly(0L);
    }

    @Test
    void reportsMissingPlaceInsteadOfEmptySummary() {
        when(repository.findByPlaceId(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.summary(404L)).isInstanceOfSatisfying(MapException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(MapErrorCode.PLACE_NOT_FOUND));
    }

    @Test
    void queriesAgainToObserveCommittedChanges() {
        when(repository.findByPlaceId(17L)).thenReturn(Optional.of(counts(2, 2)), Optional.of(counts(1, 1)));

        assertThat(service.summary(17L).totalReviewCount()).isEqualTo(2);
        assertThat(service.summary(17L).totalReviewCount()).isEqualTo(1);
        verify(repository, times(2)).findByPlaceId(17L);
    }

    private SummaryCounts counts(long reviews, long participants) {
        Map<PlaceReviewRecommendReason, Long> reasons = new EnumMap<>(PlaceReviewRecommendReason.class);
        for (var reason : PlaceReviewRecommendReason.values()) {
            reasons.put(reason, 0L);
        }
        return new SummaryCounts(reviews, participants, reasons);
    }
}
