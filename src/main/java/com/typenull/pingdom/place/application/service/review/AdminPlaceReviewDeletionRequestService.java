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

@Service
@RequiredArgsConstructor
public class AdminPlaceReviewDeletionRequestService {

    private final PlaceReviewDeletionRequestRepository deletionRequestRepository;
    private final PlaceReviewRepository placeReviewRepository;
    private final UserRepository userRepository;
    private final Clock clock;

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
