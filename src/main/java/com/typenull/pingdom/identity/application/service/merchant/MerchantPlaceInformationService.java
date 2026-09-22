package com.typenull.pingdom.identity.application.service.merchant;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantPlaceInformationResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantPlaceInformationUpdateRequest;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInformation;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceInformationRepository;
import com.typenull.pingdom.identity.event.MerchantPlaceInformationUpdatedEvent;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소 관리 권한을 확인해 사업자가 제공하는 설명·연락처·예약 링크를 조회하거나 전체 갱신.
 * 기존 행은 잠금 조회하며 저장 후 생성 여부를 포함한 변경 이벤트를 발행.
 */
@Service
@RequiredArgsConstructor
public class MerchantPlaceInformationService {

    private final MerchantPlaceInformationRepository informationRepository;
    private final MerchantPlaceInformationAccessPolicy accessPolicy;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MerchantPlaceInformationResponse get(Long actorId, Long placeId) {
        accessPolicy.requireManager(actorId, placeId);
        MerchantPlaceInformation information = informationRepository.findByPlaceId(placeId)
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.PLACE_INFORMATION_NOT_FOUND));
        return MerchantPlaceInformationResponse.from(information);
    }

    /**
     * 장소 관리 권한을 확인하고 기존 정보는 잠금 조회해 설명·연락처·웹사이트·예약 링크를 전체 갱신하거나 새로 생성.
     * 저장 결과를 반환하고 요청자·장소·생성 여부를 담은 정보 변경 이벤트를 발행.
     */
    @Transactional
    public MerchantPlaceInformationResponse upsert(
            Long actorId,
            Long placeId,
            MerchantPlaceInformationUpdateRequest request
    ) {
        accessPolicy.requireManager(actorId, placeId);
        LocalDateTime now = LocalDateTime.now(clock);
        MerchantPlaceInformation information = informationRepository.findByPlaceIdForUpdate(placeId)
                .orElse(null);
        boolean created = information == null;
        if (created) {
            information = MerchantPlaceInformation.create(
                    placeId,
                    request.description(),
                    request.contactPhone(),
                    request.websiteUrl(),
                    request.reservationUrl(),
                    actorId,
                    now
            );
        } else {
            information.update(
                    request.description(),
                    request.contactPhone(),
                    request.websiteUrl(),
                    request.reservationUrl(),
                    actorId,
                    now
            );
        }
        MerchantPlaceInformation saved = informationRepository.save(information);
        eventPublisher.publishEvent(new MerchantPlaceInformationUpdatedEvent(actorId, placeId, created, now));
        return MerchantPlaceInformationResponse.from(saved);
    }
}
