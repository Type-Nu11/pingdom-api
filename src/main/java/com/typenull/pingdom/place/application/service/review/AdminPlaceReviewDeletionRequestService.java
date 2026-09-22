package com.typenull.pingdom.place.application.service.review;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.api.dto.review.AdminPlaceReviewDeletionRequestPageResponse;
import com.typenull.pingdom.place.api.dto.review.AdminPlaceReviewDeletionRequestResponse;
import com.typenull.pingdom.place.api.dto.review.PlaceReviewDeletionRequestReviewRequest;
import com.typenull.pingdom.place.domain.review.PlaceReviewDeletionRequest;
import com.typenull.pingdom.place.domain.review.PlaceReviewDeletionRequestStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceReviewDeletionRequestRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceReviewRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 활성 관리자에게 리뷰 삭제 요청 조회·심사 기능을 제공합니다.
 * 심사 시 요청과 리뷰를 잠그며 승인만 리뷰를 DELETED로 바꾸고 거절은 숨김 상태를 복구하지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class AdminPlaceReviewDeletionRequestService {

    private final PlaceReviewDeletionRequestRepository deletionRequestRepository;
    private final PlaceReviewRepository placeReviewRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * 탈퇴·현재 정지 상태가 아닌 ADMIN인지 확인하고 선택적 심사 상태로 삭제 요청을 조회합니다.
     * 생성 시각·ID 역순이며 페이지는 1 이상, 크기는 1~100으로 보정하여 전체 건수와 함께 반환합니다.
     */
    @Transactional(readOnly = true)
    public AdminPlaceReviewDeletionRequestPageResponse list(
            Long adminUserId,
            PlaceReviewDeletionRequestStatus status,
            int page,
            int limit
    ) {
        requireActiveAdmin(adminUserId);
        int safePage = Math.max(1, page);
        int safeLimit = Math.min(100, Math.max(1, limit));
        PageRequest pageable = PageRequest.of(
                safePage - 1,
                safeLimit,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        Page<PlaceReviewDeletionRequest> requests = status == null
                ? deletionRequestRepository.findAll(pageable)
                : deletionRequestRepository.findAllByStatus(status, pageable);
        return new AdminPlaceReviewDeletionRequestPageResponse(
                requests.getContent().stream().map(AdminPlaceReviewDeletionRequestResponse::from).toList(),
                safePage,
                safeLimit,
                requests.getTotalElements(),
                requests.getTotalPages(),
                requests.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public AdminPlaceReviewDeletionRequestResponse get(Long adminUserId, Long deletionRequestId) {
        requireActiveAdmin(adminUserId);
        return AdminPlaceReviewDeletionRequestResponse.from(find(deletionRequestId));
    }

    /**
     * 활성 관리자임을 확인한 뒤 삭제 요청과 원본 리뷰를 순서대로 잠가 심사 결정을 반영합니다.
     * 승인하면 리뷰를 논리 삭제하지만 반려 시 기존 숨김 상태를 복원하지는 않습니다. 잘못된 심사 상태·입력은 전용 오류로 변환합니다.
     */
    @Transactional
    public AdminPlaceReviewDeletionRequestResponse review(
            Long adminUserId,
            Long deletionRequestId,
            PlaceReviewDeletionRequestReviewRequest request
    ) {
        requireActiveAdmin(adminUserId);
        PlaceReviewDeletionRequest deletionRequest = deletionRequestRepository.findByIdForUpdate(deletionRequestId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_REVIEW_DELETION_REQUEST_NOT_FOUND));
        var review = placeReviewRepository.findByIdForUpdate(deletionRequest.getReview().getId())
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_REVIEW_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            deletionRequest.review(adminUserId, request.decision(), request.reviewNote(), now);
            if (request.decision() == PlaceReviewDeletionRequestStatus.APPROVED) {
                review.markDeleted(now);
            }
        } catch (IllegalStateException exception) {
            throw new MapException(MapErrorCode.PLACE_REVIEW_DELETION_REQUEST_INVALID_STATE);
        } catch (IllegalArgumentException exception) {
            throw new MapException(MapErrorCode.PLACE_REVIEW_DELETION_REQUEST_INVALID_REQUEST);
        }
        return AdminPlaceReviewDeletionRequestResponse.from(deletionRequest);
    }

    private PlaceReviewDeletionRequest find(Long deletionRequestId) {
        return deletionRequestRepository.findById(deletionRequestId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_REVIEW_DELETION_REQUEST_NOT_FOUND));
    }

    private void requireActiveAdmin(Long adminUserId) {
        User admin = userRepository.findById(adminUserId).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (admin == null || admin.getRole() != UserRole.ADMIN || admin.isWithdrawn() || admin.isCurrentlyBanned(now)) {
            throw new MapException(MapErrorCode.PLACE_REVIEW_ADMIN_REQUIRED);
        }
    }
}
