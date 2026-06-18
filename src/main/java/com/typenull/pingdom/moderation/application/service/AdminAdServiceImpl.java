package com.typenull.pingdom.moderation.application.service;

import com.typenull.pingdom.moderation.api.dto.ad.AdminAdCreateRequest;
import com.typenull.pingdom.moderation.api.dto.ad.AdminAdCreateResponse;
import com.typenull.pingdom.moderation.application.AdminAdService;
import com.typenull.pingdom.moderation.domain.ad.AdminAd;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.moderation.infrastructure.persistence.AdminAdRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAdServiceImpl implements AdminAdService {

    private final AdminAdRepository adminAdRepository;

    @Override
    @Transactional
    public AdminAdCreateResponse create(AdminAdCreateRequest request) {
        if (request.startAt() == null || request.endAt() == null || !request.endAt().isAfter(request.startAt())) {
            throw new AdminException(AdminErrorCode.AD_INVALID_PERIOD);
        }

        AdminAd savedAd = adminAdRepository.save(AdminAd.builder()
                .title(request.title().trim())
                .imageUrl(request.imageUrl().trim())
                .redirectUrl(request.redirectUrl().trim())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .createdAt(LocalDateTime.now())
                .build());

        return new AdminAdCreateResponse(
                savedAd.getId(),
                savedAd.getTitle(),
                savedAd.getStartAt(),
                savedAd.getEndAt(),
                "이벤트/광고를 등록했습니다."
        );
    }
}
