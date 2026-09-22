package com.typenull.pingdom.moderation.application.service.ad;

import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;

import com.typenull.pingdom.moderation.api.dto.ad.AdminAdCreateRequest;
import com.typenull.pingdom.moderation.api.dto.ad.AdminAdCreateResponse;
import com.typenull.pingdom.moderation.api.dto.ad.AdminAdListItem;
import com.typenull.pingdom.moderation.api.dto.ad.AdminAdListResponse;
import com.typenull.pingdom.moderation.application.AdminAdService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.ad.AdminAd;
import com.typenull.pingdom.moderation.domain.ad.AdminAdDisplayStatus;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.moderation.infrastructure.persistence.AdminAdRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * 관리자 광고의 조회·등록·삭제와 감사 기록 조정.
 * 노출 기간의 시작 이상·종료 미만이면 별도 상태 저장 없이 ACTIVE로 계산.
 * 등록 시 기간 순서만 검증하며 URL의 실제 접근 가능성은 검증 범위에서 제외.
 */
@Service
@RequiredArgsConstructor
public class AdminAdServiceImpl implements AdminAdService {

    private final AdminAdRepository adminAdRepository;
    private final AdminAuditLogService adminAuditLogService;
    private final Clock clock;

    /**
     * 제목 검색·시작 기간·현재 노출 상태에 맞는 광고를 최신 생성순으로 조회.
     * page는 1~10,000, limit는 1~100으로 보정하고 같은 Clock 시각으로 조회 조건과 응답의 노출 상태를 계산.
     */
    @Override
    @Transactional(readOnly = true)
    public AdminAdListResponse list(String keyword, AdminAdDisplayStatus displayStatus,
            LocalDateTime startedFrom, LocalDateTime startedTo, int page, int limit) {
        int safePage = Math.max(1, Math.min(page, 10_000));
        int safeLimit = Math.max(1, Math.min(limit, 100));
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        LocalDateTime now = LocalDateTime.now(clock);
        Page<AdminAd> ads = adminAdRepository.findAdminAds(
                normalizedKeyword != null, normalizedKeyword,
                startedFrom != null, startedFrom,
                startedTo != null, startedTo,
                displayStatus != null,
                displayStatus == AdminAdDisplayStatus.SCHEDULED,
                displayStatus == AdminAdDisplayStatus.ACTIVE,
                displayStatus == AdminAdDisplayStatus.EXPIRED,
                now,
                PageRequest.of(safePage - 1, safeLimit, Sort.by("createdAt").descending().and(Sort.by("id").descending())));
        return new AdminAdListResponse(ads.getContent().stream().map(ad -> toItem(ad, now)).toList(),
                safePage, safeLimit, ads.getTotalElements(), ads.getTotalPages(), ads.hasNext());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminAdListItem get(Long adId) {
        AdminAd ad = adminAdRepository.findById(adId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.AD_NOT_FOUND));
        return toItem(ad, LocalDateTime.now(clock));
    }

    private AdminAdListItem toItem(AdminAd ad, LocalDateTime now) {
        AdminAdDisplayStatus status = now.isBefore(ad.getStartAt()) ? AdminAdDisplayStatus.SCHEDULED
                : now.isBefore(ad.getEndAt()) ? AdminAdDisplayStatus.ACTIVE : AdminAdDisplayStatus.EXPIRED;
        return new AdminAdListItem(ad.getId(), ad.getTitle(), ad.getImageUrl(), ad.getRedirectUrl(),
                ad.getStartAt(), ad.getEndAt(), status, ad.getCreatedAt(), ad.getUpdatedAt());
    }

    /**
     * 시작·종료 시각이 존재하고 종료가 더 늦은 광고만 생성해 새 ID와 기간을 반환.
     * 기간 오류는 AD_INVALID_PERIOD이며 광고와 생성 감사 기록은 같은 DB 트랜잭션에 저장.
     */
    @Override
    @Transactional
    public AdminAdCreateResponse create(AdminAdCreateRequest request, Long adminUserId) {
        if (request.startAt() == null || request.endAt() == null || !request.endAt().isAfter(request.startAt())) {
            throw new AdminException(AdminErrorCode.AD_INVALID_PERIOD);
        }

        AdminAd savedAd = adminAdRepository.save(AdminAd.builder()
                .title(request.title())
                .imageUrl(request.imageUrl())
                .redirectUrl(request.redirectUrl())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .createdAt(LocalDateTime.now(clock))
                .build());
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.AD_CREATED,
                AdminAuditTargetType.AD,
                savedAd.getId(),
                "AD_CREATED",
                null,
                adState(savedAd, false)
        );

        return new AdminAdCreateResponse(
                savedAd.getId(),
                savedAd.getTitle(),
                savedAd.getStartAt(),
                savedAd.getEndAt(),
                "이벤트/광고를 등록했습니다."
        );
    }

    /**
     * 존재하는 광고의 삭제와 삭제 전후 감사 기록을 같은 DB 트랜잭션에서 처리.
     * 광고 부재 시 AD_NOT_FOUND. imageUrl이 가리키는 외부 객체는 유지.
     */
    @Override
    @Transactional
    public void delete(Long adId, Long adminUserId) {
        AdminAd adminAd = adminAdRepository.findById(adId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.AD_NOT_FOUND));
        Map<String, Object> beforeState = adState(adminAd, false);

        adminAdRepository.delete(adminAd);
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.AD_DELETED,
                AdminAuditTargetType.AD,
                adId,
                "AD_DELETED",
                beforeState,
                adState(adminAd, true)
        );
    }

    private Map<String, Object> adState(AdminAd adminAd, boolean deleted) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("adId", adminAd.getId());
        state.put("title", adminAd.getTitle());
        state.put("imageUrl", adminAd.getImageUrl());
        state.put("redirectUrl", adminAd.getRedirectUrl());
        state.put("startAt", adminAd.getStartAt());
        state.put("endAt", adminAd.getEndAt());
        state.put("createdAt", adminAd.getCreatedAt());
        state.put("deleted", deleted);
        return state;
    }
}
