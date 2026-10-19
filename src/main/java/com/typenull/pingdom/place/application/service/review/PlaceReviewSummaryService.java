package com.typenull.pingdom.place.application.service.review;

import com.typenull.pingdom.place.api.dto.review.PlaceReviewSummaryResponse;
import com.typenull.pingdom.place.api.dto.review.PlaceReviewSummaryResponse.ReasonCount;
import com.typenull.pingdom.place.domain.review.PlaceReviewRecommendReason;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceReviewSummaryQueryRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaceReviewSummaryService {

    private final PlaceReviewSummaryQueryRepository summaryRepository;

    /** 캐시 없이 현재 커밋된 공개 상태를 집계하며, 존재하는 빈 장소와 없는 장소를 구분합니다. */
    @Transactional(readOnly = true)
    public PlaceReviewSummaryResponse summary(Long placeId) {
        var counts = summaryRepository.findByPlaceId(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));
        return new PlaceReviewSummaryResponse(
                placeId,
                counts.totalReviewCount(),
                counts.participantCount(),
                Arrays.stream(PlaceReviewRecommendReason.values())
                        .map(reason -> new ReasonCount(reason, counts.reasonCounts().get(reason)))
                        .toList()
        );
    }
}
