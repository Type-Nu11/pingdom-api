package com.typenull.pingdom.moderation.application.service.ad;

import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;

import com.typenull.pingdom.moderation.api.dto.ad.AdminAdCreateRequest;
import com.typenull.pingdom.moderation.api.dto.ad.AdminAdCreateResponse;
import com.typenull.pingdom.moderation.application.AdminAdService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.ad.AdminAd;
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

@Service
@RequiredArgsConstructor
public class AdminAdServiceImpl implements AdminAdService {

    private final AdminAdRepository adminAdRepository;
    private final AdminAuditLogService adminAuditLogService;
    private final Clock clock;

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
