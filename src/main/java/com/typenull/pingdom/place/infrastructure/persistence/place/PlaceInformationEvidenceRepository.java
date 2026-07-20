package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.information.PlaceInformationEvidence;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface PlaceInformationEvidenceRepository extends JpaRepository<PlaceInformationEvidence, Long> {

    List<PlaceInformationEvidence> findAllByPlace_IdOrderByUpdatedAtDescIdDesc(Long placeId);

    Optional<PlaceInformationEvidence> findByIdAndPlace_Id(Long id, Long placeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PlaceInformationEvidence> findWithLockByIdAndPlace_Id(Long id, Long placeId);
}
