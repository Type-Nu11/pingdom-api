package com.typenull.pingdom.place.infrastructure.persistence.registration;

import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachment;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachmentType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRegistrationAttachmentRepository extends JpaRepository<PlaceRegistrationAttachment, Long> {

    List<PlaceRegistrationAttachment> findAllByApplicationIdOrderByDocumentTypeAscDisplayOrderAscIdAsc(Long applicationId);

    long countByApplicationIdAndDocumentType(Long applicationId, PlaceRegistrationAttachmentType documentType);
}
