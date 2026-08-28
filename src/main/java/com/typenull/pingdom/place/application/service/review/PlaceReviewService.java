package com.typenull.pingdom.place.application.service.review;

import com.typenull.pingdom.place.api.dto.review.MyPlaceReviewPageResponse;
import com.typenull.pingdom.place.api.dto.review.MyPlaceReviewResponse;
import com.typenull.pingdom.place.api.dto.review.PlaceReviewCreateRequest;
import com.typenull.pingdom.place.api.dto.review.PlaceReviewResponse;
import com.typenull.pingdom.place.domain.review.PlaceReview;
import com.typenull.pingdom.place.domain.review.PlaceReviewVisibilityStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceReviewRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaceReviewService {

    private static final List<PlaceReviewVisibilityStatus> MY_REVIEW_VISIBILITY_STATUSES = List.of(
            PlaceReviewVisibilityStatus.VISIBLE,
            PlaceReviewVisibilityStatus.HIDDEN
    );

    private final MapPlaceRepository placeRepository;
    private final PlaceReviewRepository reviewRepository;
    private final Clock clock;

    @Transactional
    public PlaceReviewResponse create(Long userId, Long placeId, PlaceReviewCreateRequest request) {
        var place = placeRepository.findById(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));
        return PlaceReviewResponse.from(reviewRepository.save(PlaceReview.create(
                place,
                userId,
                request.recommendReason(),
                request.content(),
                request.imageUrls() == null ? List.of() : request.imageUrls(),
                LocalDateTime.now(clock)
        )));
    }

    @Transactional(readOnly = true)
    public Page<PlaceReviewResponse> list(Long placeId, int page, int limit) {
        if (!placeRepository.existsById(placeId)) {
            throw new MapException(MapErrorCode.PLACE_NOT_FOUND);
        }
        return reviewRepository.findAllByPlace_IdAndVisibilityStatus(
                        placeId,
                        PlaceReviewVisibilityStatus.VISIBLE,
                        pageRequest(page, limit)
                )
                .map(PlaceReviewResponse::from);
    }

    @Transactional(readOnly = true)
    public MyPlaceReviewPageResponse listMine(Long userId, int page, int limit) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        Page<PlaceReview> reviews = reviewRepository.findAllByUserIdAndVisibilityStatusIn(
                userId,
                MY_REVIEW_VISIBILITY_STATUSES,
                PageRequest.of(
                        safePage - 1,
                        safeLimit,
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
                )
        );

        return new MyPlaceReviewPageResponse(
                reviews.getContent().stream().map(MyPlaceReviewResponse::from).toList(),
                safePage,
                safeLimit,
                reviews.getTotalElements(),
                reviews.getTotalPages(),
                reviews.hasNext()
        );
    }

    private PageRequest pageRequest(int page, int limit) {
        return PageRequest.of(
                Math.max(page - 1, 0),
                Math.min(Math.max(limit, 1), 100),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
    }
}
