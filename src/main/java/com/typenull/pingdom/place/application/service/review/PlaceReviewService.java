package com.typenull.pingdom.place.application.service.review;

import com.typenull.pingdom.place.api.dto.review.MyPlaceReviewPageResponse;
import com.typenull.pingdom.place.api.dto.review.MyPlaceReviewResponse;
import com.typenull.pingdom.place.api.dto.review.PlaceReviewCreateRequest;
import com.typenull.pingdom.place.api.dto.review.PlaceReviewResponse;
import com.typenull.pingdom.place.domain.review.PlaceReview;
import com.typenull.pingdom.place.domain.review.PlaceReviewRecommendReason;
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

/**
 * 장소 리뷰와 사전 업로드 사진 연결을 한 쓰기 흐름으로 처리합니다.
 * 공개 목록은 VISIBLE만, 내 리뷰 목록은 VISIBLE·HIDDEN을 반환하며 복수 추천 사유의 첫 값을 기존 단일 필드에도 기록합니다.
 */
@Service
@RequiredArgsConstructor
public class PlaceReviewService {

    private static final List<PlaceReviewVisibilityStatus> MY_REVIEW_VISIBILITY_STATUSES = List.of(
            PlaceReviewVisibilityStatus.VISIBLE,
            PlaceReviewVisibilityStatus.HIDDEN
    );

    private final MapPlaceRepository placeRepository;
    private final PlaceReviewRepository reviewRepository;
    private final PlaceReviewMediaService reviewMediaService;
    private final Clock clock;

    /**
     * 존재하는 장소에 리뷰를 저장하고 본인의 사전 업로드 사진을 같은 트랜잭션에서 연결해 응답합니다.
     * 복수 추천 사유가 있으면 첫 값을 기존 단일 사유에도 기록하고 사진 연결 실패는 리뷰 저장도 실패시킵니다. 장소의 공개·영업 상태를 별도로 제한하지는 않습니다.
     */
    @Transactional
    public PlaceReviewResponse create(Long userId, Long placeId, PlaceReviewCreateRequest request) {
        var place = placeRepository.findById(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));
        List<PlaceReviewRecommendReason> recommendReasons = request.recommendReasons() == null
                ? List.of()
                : request.recommendReasons();
        String legacyRecommendReason = recommendReasons.isEmpty()
                ? request.recommendReason()
                : recommendReasons.getFirst().name();
        PlaceReview review = reviewRepository.save(PlaceReview.create(
                place,
                userId,
                legacyRecommendReason,
                recommendReasons,
                request.content(),
                List.of(),
                LocalDateTime.now(clock)
        ));
        reviewMediaService.connect(userId, placeId, review, request.reviewMediaIds());
        return PlaceReviewResponse.from(review);
    }

    /**
     * 장소 존재를 확인한 뒤 VISIBLE 리뷰만 생성 시각·ID 역순 페이지로 반환합니다.
     * 페이지는 1 이상, 크기는 1~100으로 보정하며 숨김·삭제 리뷰는 제외합니다.
     */
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

    /**
     * 사용자 본인의 VISIBLE·HIDDEN 리뷰를 생성 시각·ID 역순으로 조회하고 페이지 메타데이터와 함께 반환합니다.
     * DELETED 리뷰는 제외하며 페이지는 1 이상, 크기는 1~100으로 보정합니다.
     */
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
