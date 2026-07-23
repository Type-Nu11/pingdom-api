package com.typenull.pingdom.moderation.application.service.place.duplicate;

import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceMergeRequest;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceMergeResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminPlaceDuplicateCandidateListResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminPlaceDuplicateCandidateResponse;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.application.service.place.quality.AdminMapPlaceService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.moderation.domain.place.PlaceDuplicateCandidate;
import com.typenull.pingdom.moderation.domain.place.PlaceDuplicateDecisionStatus;
import com.typenull.pingdom.moderation.infrastructure.persistence.PlaceDuplicateCandidateRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminPlaceDuplicateService {

    private final PlaceDuplicateCandidateRepository candidateRepository;
    private final AdminMapPlaceService adminMapPlaceService;
    private final AdminAuditLogService adminAuditLogService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminPlaceDuplicateCandidateListResponse list(PlaceDuplicateDecisionStatus status) {
        List<AdminPlaceDuplicateCandidateResponse> candidates = candidateRepository
                .findByStatusOrderByDetectedAtDescIdDesc(status)
                .stream()
                .map(this::toResponse)
                .toList();
        return new AdminPlaceDuplicateCandidateListResponse(candidates, candidates.size());
    }

    @Transactional(readOnly = true)
    public AdminPlaceDuplicateCandidateResponse get(Long candidateId) {
        return toResponse(candidateRepository.findById(candidateId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_DUPLICATE_CANDIDATE_NOT_FOUND)));
    }

    @Transactional
    public AdminPlaceDuplicateCandidateResponse confirm(Long adminUserId, Long candidateId, String reviewNote) {
        return decide(adminUserId, candidateId, reviewNote, true);
    }

    @Transactional
    public AdminPlaceDuplicateCandidateResponse reject(Long adminUserId, Long candidateId, String reviewNote) {
        return decide(adminUserId, candidateId, reviewNote, false);
    }

    @Transactional
    public AdminMapPlaceMergeResponse merge(Long adminUserId, Long candidateId, Long targetPlaceId) {
        PlaceDuplicateCandidate candidate = findForUpdate(candidateId);
        if (candidate.getStatus() != PlaceDuplicateDecisionStatus.CONFIRMED) {
            throw new AdminException(AdminErrorCode.PLACE_MERGE_NOT_ALLOWED);
        }
        if (!targetPlaceId.equals(candidate.getLeftPlaceId()) && !targetPlaceId.equals(candidate.getRightPlaceId())) {
            throw new AdminException(AdminErrorCode.PLACE_MERGE_INVALID_REQUEST);
        }
        Long sourcePlaceId = targetPlaceId.equals(candidate.getLeftPlaceId())
                ? candidate.getRightPlaceId()
                : candidate.getLeftPlaceId();
        return adminMapPlaceService.mergePlaces(
                adminUserId,
                new AdminMapPlaceMergeRequest(sourcePlaceId, targetPlaceId, candidateId)
        );
    }

    private AdminPlaceDuplicateCandidateResponse decide(
            Long adminUserId,
            Long candidateId,
            String reviewNote,
            boolean confirmed
    ) {
        if (adminUserId == null || !StringUtils.hasText(reviewNote)) {
            throw new AdminException(AdminErrorCode.PLACE_MERGE_INVALID_REQUEST);
        }
        PlaceDuplicateCandidate candidate = findForUpdate(candidateId);
        if (candidate.getStatus() != PlaceDuplicateDecisionStatus.PENDING) {
            throw new AdminException(AdminErrorCode.PLACE_DUPLICATE_DECISION_ALREADY_COMPLETED);
        }
        Map<String, Object> beforeState = Map.of("status", candidate.getStatus().name());
        LocalDateTime now = LocalDateTime.now(clock);
        if (confirmed) {
            candidate.confirm(adminUserId, reviewNote, now);
        } else {
            candidate.reject(adminUserId, reviewNote, now);
        }
        adminAuditLogService.record(
                adminUserId,
                confirmed ? AdminAuditAction.PLACE_DUPLICATE_CONFIRMED : AdminAuditAction.PLACE_DUPLICATE_REJECTED,
                AdminAuditTargetType.PLACE_DUPLICATE_CANDIDATE,
                candidate.getId(),
                reviewNote.trim(),
                beforeState,
                Map.of(
                        "status", candidate.getStatus().name(),
                        "leftPlaceId", candidate.getLeftPlaceId(),
                        "rightPlaceId", candidate.getRightPlaceId()
                )
        );
        return toResponse(candidate);
    }

    private PlaceDuplicateCandidate findForUpdate(Long candidateId) {
        return candidateRepository.findByIdForUpdate(candidateId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_DUPLICATE_CANDIDATE_NOT_FOUND));
    }

    private AdminPlaceDuplicateCandidateResponse toResponse(PlaceDuplicateCandidate candidate) {
        return new AdminPlaceDuplicateCandidateResponse(
                candidate.getId(),
                candidate.getLeftPlaceId(),
                candidate.getRightPlaceId(),
                candidate.getMatchReason().name(),
                candidate.getConfidenceScore(),
                candidate.getDistanceMeters(),
                candidate.getStatus().name(),
                candidate.getReviewedByAdminUserId(),
                candidate.getReviewNote(),
                candidate.getMergeHistoryId(),
                candidate.getDetectedAt(),
                candidate.getReviewedAt(),
                candidate.getUpdatedAt()
        );
    }
}
