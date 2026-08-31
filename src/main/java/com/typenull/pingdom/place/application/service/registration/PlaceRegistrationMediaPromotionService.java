package com.typenull.pingdom.place.application.service.registration;

import com.typenull.pingdom.place.application.support.PlaceMediaStorageKey;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.media.PlaceMedia;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachment;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachmentType;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceMediaRepository;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 승인된 신규 장소 신청의 공개 대표 이미지만 exploration media로 승격합니다.
 * 사업자등록증과 신분증은 이 서비스의 대상이 아니므로 private prefix 밖으로 복사되지 않습니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceRegistrationMediaPromotionService {

    private final PlaceMediaRepository placeMediaRepository;
    private final S3ObjectStorage storage;
    private final Clock clock;

    public PromotionResult promote(MapPlace place, PlaceRegistrationApplication application) {
        // 기존 COMPLETED 데이터도 같은 경로로 복구하므로 이미 운영 중인 설명은 덮어쓰지 않습니다.
        if (place.getDescription() == null) {
            place.updateDescription(application.getDescription());
        }
        List<PlaceRegistrationAttachment> representativeImages = application.getAttachments().stream()
                .filter(PlaceRegistrationAttachment::isActive)
                .filter(attachment -> attachment.getDocumentType() == PlaceRegistrationAttachmentType.REPRESENTATIVE_IMAGE)
                .sorted(Comparator.comparing(PlaceRegistrationAttachment::getDisplayOrder)
                        .thenComparing(PlaceRegistrationAttachment::getId))
                .toList();

        int promotedCount = 0;
        int alreadyPromotedCount = 0;
        for (PlaceRegistrationAttachment attachment : representativeImages) {
            if (attachment.getId() == null
                    || placeMediaRepository.findBySourceRegistrationAttachmentId(attachment.getId()).isPresent()) {
                alreadyPromotedCount++;
                continue;
            }
            String targetKey = PlaceMediaStorageKey.createRegistrationExplorationKey(
                    place.getId(),
                    application.getApplicantUserId(),
                    attachment.getId(),
                    extensionOf(attachment.getOriginalFilename(), attachment.getContentType())
            );
            storage.copy(attachment.getStorageKey(), targetKey);
            deleteCopiedObjectOnRollback(targetKey);

            String imageUrl = storage.publicUrl(targetKey);
            PlaceMedia media = placeMediaRepository.save(PlaceMedia.explorationFromRegistrationAttachment(
                    place,
                    imageUrl,
                    targetKey,
                    attachment.getId(),
                    attachment.getDisplayOrder(),
                    LocalDateTime.now(clock)
            ));
            // map_place.image_url is the canonical list/marker image; user map_image is only a fallback.
            if (place.getImageUrl() == null) {
                place.updateImageUrl(media.getImageUrl());
            }
            promotedCount++;
        }
        return new PromotionResult(promotedCount, alreadyPromotedCount);
    }

    /** 백필 runner가 신규 승격과 기존 승격 건을 운영 로그에서 구분할 수 있게 합니다. */
    public record PromotionResult(int promotedCount, int alreadyPromotedCount) {

        public boolean hasPromotedMedia() {
            return promotedCount > 0;
        }
    }

    private void deleteCopiedObjectOnRollback(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    try {
                        storage.delete(key);
                    } catch (RuntimeException exception) {
                        // 재시도 가능한 orphan만 남기고 승인 transaction의 원래 실패 원인을 보존합니다.
                        log.warn("Approved place media rollback cleanup failed. key={}", key, exception);
                    }
                }
            }
        });
    }

    private String extensionOf(String filename, String contentType) {
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot >= 0 && dot < filename.length() - 1) {
                String extension = filename.substring(dot + 1).toLowerCase();
                if (extension.matches("[a-z0-9]{1,10}")) {
                    return extension;
                }
            }
        }
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }
}
