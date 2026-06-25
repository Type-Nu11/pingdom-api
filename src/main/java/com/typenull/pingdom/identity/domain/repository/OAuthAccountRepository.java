package com.typenull.pingdom.identity.domain.repository;

import com.typenull.pingdom.identity.domain.AuthProvider;
import com.typenull.pingdom.identity.domain.OAuthAccount;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, Long> {
    Optional<OAuthAccount> findByProviderAndProviderId(AuthProvider provider, String providerId);

    @EntityGraph(attributePaths = "user")
    Optional<OAuthAccount> findWithUserByProviderAndProviderId(AuthProvider provider, String providerId);

    Optional<OAuthAccount> findByUser_IdAndProvider(Long userId, AuthProvider provider);

    long countByUser_Id(Long userId);

    @Modifying
    @Query("""
            DELETE FROM OAuthAccount o
            WHERE o.user.id IN :userIds
            """)
    int deleteAllByUserIds(@Param("userIds") Collection<Long> userIds);
}
