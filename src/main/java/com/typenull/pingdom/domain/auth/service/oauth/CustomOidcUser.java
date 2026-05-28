package com.typenull.pingdom.domain.auth.service.oauth;

import com.typenull.pingdom.domain.auth.domain.AuthProvider;
import com.typenull.pingdom.domain.auth.domain.UserRole;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

@Getter
public class CustomOidcUser implements OidcUser, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String username;
    private final UserRole role;
    private final AuthProvider provider;
    private final String providerId;
    private final OidcUser delegate;

    public CustomOidcUser(
            Long userId,
            String username,
            UserRole role,
            AuthProvider provider,
            String providerId,
            OidcUser delegate
    ) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.provider = provider;
        this.providerId = providerId;
        this.delegate = delegate;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return delegate.getAuthorities();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Map<String, Object> getClaims() {
        return delegate.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return delegate.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
        return delegate.getIdToken();
    }
}

