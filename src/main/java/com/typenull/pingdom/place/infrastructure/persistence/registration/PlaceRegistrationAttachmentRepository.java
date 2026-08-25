package com.typenull.pingdom.place.infrastructure.persistence.registration;

import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachment;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachmentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRegistrationAttachmentRepository extends JpaRepository<PlaceRegistrationAttachment, Long> {

    List<PlaceRegistrationAttachment> findAllByApplicationIdOrderByDocumentTypeAscDisplayOrderAscIdAsc(Long applicationId);

    List<PlaceRegistrationAttachment> findAllByApplicationIdAndDocumentTypeOrderByDisplayOrderAscIdAsc(
            Long applicationId,
            PlaceRegistrationAttachmentType documentType
    );

    Optional<PlaceRegistrationAttachment> findByIdAndApplicationId(Long id, Long applicationId);

    long countByApplicationIdAndDocumentType(Long applicationId, PlaceRegistrationAttachmentType documentType);
}
