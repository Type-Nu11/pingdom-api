package com.typenull.pingdom.moderation.application.service.place.duplicate;

import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceMergeRequest;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceMergeResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminPlaceDuplicateCandidateListResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminPlaceDuplicateCandidateResponse;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.application.service.place.merge.AdminPlaceMergeService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.moderation.domain.place.PlaceDuplicateCandidate;
import com.typenull.pingdom.moderation.domain.place.PlaceDuplicateDecisionStatus;
import com.typenull.pingdom.moderation.domain.place.PlaceDuplicateMatchReason;
import com.typenull.pingdom.moderation.infrastructure.persistence.PlaceDuplicateCandidateRepository;
import com.typenull.pingdom.moderation.outbox.notification.AdminNotificationOutboxPublisher;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 장소 ID 쌍을 작은 ID·큰 ID 순으로 정규화해 중복 후보를 저장하고 관리자 판정·병합으로 연결.
 * 재검출 시 기존 후보의 점수·상태는 유지. 판정·병합은 후보를 잠그며 최초 검출 경쟁은 자체 재시도 없이 전파.
 */
@Service
@RequiredArgsConstructor
public class AdminPlaceDuplicateService {

    private final PlaceDuplicateCandidateRepository candidateRepository;
    private final AdminPlaceMergeService adminPlaceMergeService;
    private final AdminAuditLogService adminAuditLogService;
    private final AdminNotificationOutboxPublisher adminNotificationOutboxPublisher;
    private final Clock clock;

    /**
     * 장소 ID 쌍을 작은 ID·큰 ID 순으로 조회해 기존 후보가 있으면 점수·상태를 유지한 채 반환.
     * 새 후보만 도메인 입력 검증 후 저장하고 관리자 알림 outbox를 같은 트랜잭션에 등록. 최초 삽입 경쟁은 재시도 없이 전파.
     */
    @Transactional
    public AdminPlaceDuplicateCandidateResponse detect(
            Long firstPlaceId,
            Long secondPlaceId,
            PlaceDuplicateMatchReason matchReason,
            BigDecimal confidenceScore,
            Integer distanceMeters
    ) {
        Objects.requireNonNull(firstPlaceId, "firstPlaceId must not be null");
        Objects.requireNonNull(secondPlaceId, "secondPlaceId must not be null");
        long leftPlaceId = Math.min(firstPlaceId, secondPlaceId);
        long rightPlaceId = Math.max(firstPlaceId, secondPlaceId);
        return candidateRepository.findByLeftPlaceIdAndRightPlaceId(leftPlaceId, rightPlaceId)
                .map(this::toResponse)
                .orElseGet(() -> saveDetectedCandidate(
                        firstPlaceId,
                        secondPlaceId,
                        matchReason,
                        confidenceScore,
                        distanceMeters
                ));
    }

    /**
     * 지정된 판정 상태의 중복 후보를 검출 시각·ID 내림차순으로 조회. page는 1 이상·limit는 1~100으로 보정.
     */
    @Transactional(readOnly = true)
    public AdminPlaceDuplicateCandidateListResponse list(PlaceDuplicateDecisionStatus status, int page, int limit) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        var result = candidateRepository.findByStatus(status,
                PageRequest.of(safePage - 1, safeLimit, Sort.by("detectedAt").descending().and(Sort.by("id").descending())));
        List<AdminPlaceDuplicateCandidateResponse> candidates = result.getContent()
                .stream()
                .map(this::toResponse)
                .toList();
        return new AdminPlaceDuplicateCandidateListResponse(
                candidates, result.getNumber() + 1, result.getSize(), result.getTotalElements(), result.getTotalPages(), result.hasNext());
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

    /**
     * 후보 행을 잠그고 CONFIRMED 상태이며 대상 ID가 후보의 두 장소 중 하나인지 확인.
     * 조건이 맞지 않으면 병합 오류로 거절하고 나머지 장소를 원본으로 정해 동일 트랜잭션의 병합 서비스에 처리를 위임.
     */
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
        return adminPlaceMergeService.mergePlaces(
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

    private AdminPlaceDuplicateCandidateResponse saveDetectedCandidate(
            Long firstPlaceId,
            Long secondPlaceId,
            PlaceDuplicateMatchReason matchReason,
            BigDecimal confidenceScore,
            Integer distanceMeters
    ) {
        PlaceDuplicateCandidate candidate = candidateRepository.save(PlaceDuplicateCandidate.detect(
                firstPlaceId,
                secondPlaceId,
                matchReason,
                confidenceScore,
                distanceMeters,
                LocalDateTime.now(clock)
        ));
        adminNotificationOutboxPublisher.publishDuplicatePlaceDetected(
                candidate.getId(),
                candidate.getLeftPlaceId(),
                candidate.getRightPlaceId()
        );
        return toResponse(candidate);
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
