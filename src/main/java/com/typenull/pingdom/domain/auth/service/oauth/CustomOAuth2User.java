package com.typenull.pingdom.domain.auth.service.oauth;

import com.typenull.pingdom.domain.auth.domain.AuthProvider;
import com.typenull.pingdom.domain.auth.domain.UserRole;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

@Getter
public class CustomOAuth2User implements OAuth2User, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String username;
    private final UserRole role;
    private final AuthProvider provider;
    private final String providerId;
    private final Collection<? extends GrantedAuthority> authorities;
    private final Map<String, Object> attributes;
    private final String nameAttributeKey;

    public CustomOAuth2User(
            Long userId,
            String username,
            UserRole role,
            AuthProvider provider,
            String providerId,
            Collection<? extends GrantedAuthority> authorities,
            Map<String, Object> attributes,
            String nameAttributeKey
    ) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.provider = provider;
        this.providerId = providerId;
        this.authorities = authorities;
        this.attributes = attributes;
        this.nameAttributeKey = nameAttributeKey;
    }

    @Override
    public String getName() {
        Object value = attributes.get(nameAttributeKey);
        return (value == null) ? null : String.valueOf(value);
    }
}

