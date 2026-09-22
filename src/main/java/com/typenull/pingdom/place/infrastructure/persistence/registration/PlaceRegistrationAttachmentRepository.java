package com.typenull.pingdom.place.infrastructure.persistence.registration;

import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachment;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachmentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 신청에 귀속된 첨부를 문서 유형·표시 순서·ID 순으로 조회합니다.
 * 삭제 여부와 보존 기한은 쿼리에서 제외하지 않으므로 열람 용도에 맞는 필터는 서비스가 적용합니다.
 */
public interface PlaceRegistrationAttachmentRepository extends JpaRepository<PlaceRegistrationAttachment, Long> {

    List<PlaceRegistrationAttachment> findAllByApplicationIdOrderByDocumentTypeAscDisplayOrderAscIdAsc(Long applicationId);

    List<PlaceRegistrationAttachment> findAllByApplicationIdAndDocumentTypeOrderByDisplayOrderAscIdAsc(
            Long applicationId,
            PlaceRegistrationAttachmentType documentType
    );

    Optional<PlaceRegistrationAttachment> findByIdAndApplicationId(Long id, Long applicationId);

    long countByApplicationIdAndDocumentType(Long applicationId, PlaceRegistrationAttachmentType documentType);
}
