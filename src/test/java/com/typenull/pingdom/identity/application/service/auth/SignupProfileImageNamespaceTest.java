package com.typenull.pingdom.identity.application.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.api.dto.signup.SignupRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;

class SignupProfileImageNamespaceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);
    private final AuthServiceImpl service = new AuthServiceImpl(users, null, encoder, null, outbox,
            null, null, null, null, Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC), null);

    @ParameterizedTest
    @ValueSource(strings = {
            "https://bucket.s3.ap-northeast-2.amazonaws.com/users/profile-images/tracked/id.png",
            "https://s3.ap-northeast-2.amazonaws.com/bucket/users/profile-images/tracked/id.png?X-Amz-Signature=signature",
            "https://cdn.example.com/users/profile-images/tracked/id.png",
            "https://bucket.s3.amazonaws.com/%75sers/profile-images/%74racked/id.png",
            "https://bucket.s3.amazonaws.com/users%2Fprofile-images%2Ftracked%2Fid.png",
            "https://bucket.s3.amazonaws.com/users/profile-images/temp/%2e%2e/tracked/id.png",
            "https://bucket.s3.amazonaws.com/users/profile-images/tracked/id.png?invalid=%ZZ",
            " https://bucket.s3.amazonaws.com/users/profile-images/tracked/id.png ",
            "https://bucket.s3.amazonaws.com/users/prof\nile-images/tracked/id.png",
            "https:\\bucket.s3.amazonaws.com\\users\\profile-images\\tracked\\id.png",
            "https:bucket.s3.amazonaws.com/users/profile-images/tracked/id.png",
            "/users/profile-images/tracked/id.png"
    })
    void rejectsReservedNamespaceBeforeAnyUserWrite(String url) {
        assertThatThrownBy(() -> service.signup(request(url))).isInstanceOfSatisfying(AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.PROFILE_IMAGE_URL_RESERVED));
        verifyNoInteractions(users, encoder, outbox);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "https://external.example.com/profile.png",
            "https://bucket.s3.amazonaws.com/users/profile-images/123/legacy.png",
            "https://external.example.com/users/profile-images/tracked-other/id.png",
            "https://external.example.com/profile.png?next=/users/profile-images/tracked/id.png"
    })
    void preservesExternalAndLegacyUrlContract(String url) {
        when(encoder.encode(anyString())).thenReturn("encoded");
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.signup(request(url)).profileImageUrl()).isEqualTo(url);
    }

    private SignupRequest request(String url) {
        return new SignupRequest("new-user", "new@example.invalid", "password123", 1995, url, "ko", "KR");
    }
}
