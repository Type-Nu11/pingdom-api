package com.typenull.pingdom.domain.auth.repository;

import com.typenull.pingdom.domain.auth.domain.AuthProvider;
import com.typenull.pingdom.domain.auth.domain.OAuthAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, Long> {
    Optional<OAuthAccount> findByProviderAndProviderId(AuthProvider provider, String providerId);
}

