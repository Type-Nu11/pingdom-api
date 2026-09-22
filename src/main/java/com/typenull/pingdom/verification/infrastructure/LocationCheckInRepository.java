package com.typenull.pingdom.verification.infrastructure;

import com.typenull.pingdom.verification.domain.LocationCheckIn;
import com.typenull.pingdom.verification.domain.LocationCheckInStatus;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** 장소별 체크인 존재, 사용자·장소·날짜별 중복 및 본인 기록 조회를 제공한다. */
public interface LocationCheckInRepository extends JpaRepository<LocationCheckIn, Long> {
    boolean existsByPlaceId(Long placeId);
    /** 상태와 무관하게 같은 사용자·장소·날짜의 체크인이 있으면 일일 중복으로 본다. */
    boolean existsByTouristUserIdAndPlaceIdAndCheckInDate(Long touristUserId, Long placeId, LocalDate checkInDate);
    /** 일일 체크인에 지정 상태까지 요구한다. 체류 인증 완료 여부를 구분할 때 사용한다. */
    boolean existsByTouristUserIdAndPlaceIdAndCheckInDateAndStatus(Long touristUserId, Long placeId,
            LocalDate checkInDate, LocationCheckInStatus status);
    Page<LocationCheckIn> findAllByTouristUserId(Long touristUserId, Pageable pageable);
    /** ID가 존재해도 사용자 ID가 다르면 빈 결과로 반환해 소유권 확인에 사용한다. */
    Optional<LocationCheckIn> findByIdAndTouristUserId(Long id, Long touristUserId);
}
