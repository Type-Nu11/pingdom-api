package com.typenull.pingdom.identity.infrastructure.oauth;

import com.typenull.pingdom.identity.domain.AuthProvider;
import com.typenull.pingdom.identity.domain.UserRole;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * OAuth 제공자 속성과 로컬 회원 ID·역할을 함께 보관하는 인증 principal입니다.
 * 식별 이름은 지정한 제공자 속성에서 읽고 없는 값은 null로 반환합니다.
 */
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
