package com.typenull.pingdom.notification.repository;

import com.typenull.pingdom.notification.domain.FcmDeviceToken;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FcmDeviceTokenRepository extends JpaRepository<FcmDeviceToken, Long> {

    Optional<FcmDeviceToken> findByToken(String token);

    List<FcmDeviceToken> findAllByUserIdOrderByUpdatedAtDesc(Long userId);

    @Modifying
    @Query("""
            DELETE FROM FcmDeviceToken token
            WHERE token.userId = :userId
              AND token.token = :token
            """)
    int deleteByUserIdAndToken(
            @Param("userId") Long userId,
            @Param("token") String token
    );

    @Modifying
    @Query("""
            DELETE FROM FcmDeviceToken token
            WHERE token.token = :token
            """)
    int deleteByToken(@Param("token") String token);

    @Modifying
    @Query("""
            DELETE FROM FcmDeviceToken token
            WHERE token.userId = :userId
            """)
    int deleteAllByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("""
            DELETE FROM FcmDeviceToken token
            WHERE token.userId IN :userIds
            """)
    int deleteAllByUserIds(@Param("userIds") Collection<Long> userIds);
}
