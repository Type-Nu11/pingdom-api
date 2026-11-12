package com.typenull.pingdom.identity.domain.repository;

import com.typenull.pingdom.identity.domain.ProfileImageUploadIntent;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfileImageUploadIntentRepository extends JpaRepository<ProfileImageUploadIntent, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ProfileImageUploadIntent i where i.id = :id")
    Optional<ProfileImageUploadIntent> findForUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProfileImageUploadIntent> findByUserIdAndImageUrl(Long userId, String imageUrl);

    @Query(value = """
            SELECT * FROM profile_image_upload_intent
            WHERE next_check_at <= :now
            ORDER BY next_check_at, id LIMIT :limit FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ProfileImageUploadIntent> findDueForUpdate(@Param("now") Instant now, @Param("limit") int limit);
}
