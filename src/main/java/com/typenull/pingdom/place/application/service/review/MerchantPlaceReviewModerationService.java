package com.typenull.pingdom.place.application.service.review;

import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.place.api.dto.review.MerchantPlaceReviewDeletionRequestResponse;
import com.typenull.pingdom.place.api.dto.review.MerchantPlaceReviewPageResponse;
import com.typenull.pingdom.place.api.dto.review.MerchantPlaceReviewResponse;
import com.typenull.pingdom.place.api.dto.review.PlaceReviewDeletionRequestCreateRequest;
import com.typenull.pingdom.place.domain.review.PlaceReview;
import com.typenull.pingdom.place.domain.review.PlaceReviewDeletionRequest;
import com.typenull.pingdom.place.domain.review.PlaceReviewDeletionRequestStatus;
import com.typenull.pingdom.place.domain.review.PlaceReviewVisibilityStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceReviewDeletionRequestRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceReviewRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소 소유자의 리뷰 관리 목록과 삭제 심사 요청을 처리.
 * 목록은 숨김·삭제 이력도 포함하고 삭제 요청은 리뷰를 먼저 숨긴 뒤 관리자 심사를 대기.
 */
@Service
@RequiredArgsConstructor
public class MerchantPlaceReviewModerationService {

    private static final List<PlaceReviewVisibilityStatus> MERCHANT_REVIEW_VISIBILITY_STATUSES = List.of(
            PlaceReviewVisibilityStatus.VISIBLE,
            PlaceReviewVisibilityStatus.HIDDEN,
            PlaceReviewVisibilityStatus.DELETED
    );

    private final MerchantOwnerPlaceRepository merchantOwnerPlaceRepository;
    private final PlaceReviewRepository placeReviewRepository;
    private final PlaceReviewDeletionRequestRepository deletionRequestRepository;
    private final Clock clock;

    /**
     * 현재 장소 소유 연결을 확인하고 공개·숨김·삭제 리뷰를 생성 시각·ID 역순으로 조회.
     * 각 리뷰의 최신 삭제 요청을 일괄 조합하며 페이지는 1 이상, 크기는 1~100으로 보정.
     */
    @Transactional(readOnly = true)
    public MerchantPlaceReviewPageResponse list(Long merchantOwnerUserId, Long placeId, int page, int limit) {
        requireOwner(merchantOwnerUserId, placeId);

        int safePage = Math.max(page, 1);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        Page<PlaceReview> reviews = placeReviewRepository.findAllByPlace_IdAndVisibilityStatusIn(
                placeId,
                MERCHANT_REVIEW_VISIBILITY_STATUSES,
                PageRequest.of(
                        safePage - 1,
                        safeLimit,
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
                )
        );
        Map<Long, PlaceReviewDeletionRequest> latestRequests = latestRequests(reviews.getContent());

        return new MerchantPlaceReviewPageResponse(
                reviews.getContent().stream()
                        .map(review -> MerchantPlaceReviewResponse.from(review, latestRequests.get(review.getId())))
                        .toList(),
                safePage,
                safeLimit,
                reviews.getTotalElements(),
                reviews.getTotalPages(),
                reviews.hasNext()
        );
    }

    /**
     * 현재 장소 소유자만 해당 장소의 리뷰를 잠가 숨기고 관리자 삭제 심사를 요청할 수 있음.
     * 진행 중 요청은 사전 조회와 지정 유일 제약 위반으로 거절하며 상태·사유 오류를 구분해 반환. 요청 실패 시 숨김 변경도 같은 트랜잭션에서 롤백됨.
     */
    @Transactional
    public MerchantPlaceReviewDeletionRequestResponse requestDeletion(
            Long merchantOwnerUserId,
            Long placeId,
            Long reviewId,
            PlaceReviewDeletionRequestCreateRequest request
    ) {
        requireOwner(merchantOwnerUserId, placeId);
        var review = placeReviewRepository.findByIdAndPlaceIdForUpdate(reviewId, placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_REVIEW_NOT_FOUND));
        if (deletionRequestRepository.existsByReview_IdAndStatus(reviewId, PlaceReviewDeletionRequestStatus.PENDING)) {
            throw new MapException(MapErrorCode.PLACE_REVIEW_DELETION_REQUEST_ALREADY_PENDING);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        try {
            review.hide(now);
            PlaceReviewDeletionRequest deletionRequest = PlaceReviewDeletionRequest.submit(
                    review,
                    merchantOwnerUserId,
                    request.requestReason(),
                    now
            );
            return MerchantPlaceReviewDeletionRequestResponse.from(deletionRequestRepository.saveAndFlush(deletionRequest));
        } catch (IllegalStateException exception) {
            throw new MapException(MapErrorCode.PLACE_REVIEW_DELETION_REQUEST_INVALID_STATE);
        } catch (IllegalArgumentException exception) {
            throw new MapException(MapErrorCode.PLACE_REVIEW_DELETION_REQUEST_INVALID_REQUEST);
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, "uq_place_review_deletion_request_pending")) {
                throw new MapException(MapErrorCode.PLACE_REVIEW_DELETION_REQUEST_ALREADY_PENDING);
            }
            throw exception;
        }
    }

    private void requireOwner(Long merchantOwnerUserId, Long placeId) {
        if (!merchantOwnerPlaceRepository.existsByPlaceIdAndMerchantOwnerUserId(placeId, merchantOwnerUserId)) {
            throw new MapException(MapErrorCode.PLACE_REVIEW_MANAGEMENT_FORBIDDEN);
        }
    }

    private Map<Long, PlaceReviewDeletionRequest> latestRequests(Collection<PlaceReview> reviews) {
        if (reviews.isEmpty()) {
            return Map.of();
        }
        return deletionRequestRepository.findAllByReview_IdInOrderByReview_IdAscCreatedAtDescIdDesc(
                        reviews.stream().map(PlaceReview::getId).toList()
                )
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        request -> request.getReview().getId(),
                        Function.identity(),
                        (latest, ignored) -> latest
                ));
    }

    private boolean hasConstraint(Throwable throwable, String constraintName) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation
                    && constraintName.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
            if (current instanceof SQLException sqlException
                    && constraintName.equalsIgnoreCase(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
