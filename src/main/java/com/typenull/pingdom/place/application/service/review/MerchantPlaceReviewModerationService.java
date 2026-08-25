package com.typenull.pingdom.place.application.service.review;

import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.place.api.dto.review.MerchantPlaceReviewDeletionRequestResponse;
import com.typenull.pingdom.place.api.dto.review.PlaceReviewDeletionRequestCreateRequest;
import com.typenull.pingdom.place.domain.review.PlaceReviewDeletionRequest;
import com.typenull.pingdom.place.domain.review.PlaceReviewDeletionRequestStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceReviewDeletionRequestRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceReviewRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantPlaceReviewModerationService {

    private final MerchantOwnerPlaceRepository merchantOwnerPlaceRepository;
    private final PlaceReviewRepository placeReviewRepository;
    private final PlaceReviewDeletionRequestRepository deletionRequestRepository;
    private final Clock clock;

    @Transactional
    public MerchantPlaceReviewDeletionRequestResponse requestDeletion(
            Long merchantOwnerUserId,
            Long placeId,
            Long reviewId,
            PlaceReviewDeletionRequestCreateRequest request
    ) {
        if (!merchantOwnerPlaceRepository.existsByPlaceIdAndMerchantOwnerUserId(placeId, merchantOwnerUserId)) {
            throw new MapException(MapErrorCode.PLACE_REVIEW_MANAGEMENT_FORBIDDEN);
        }
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
