package com.typenull.pingdom.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserCurrentActivityIntentRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.domain.travel.CurrentActivityIntent;
import com.typenull.pingdom.identity.domain.travel.UserCurrentActivityIntent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CurrentActivityIntentServiceTest {

    private static final long USER_ID = 1L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-01T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserCurrentActivityIntentRepository currentActivityIntentRepository;

    private CurrentActivityIntentService currentActivityIntentService;

    @BeforeEach
    void setUp() {
        currentActivityIntentService = new CurrentActivityIntentService(
                userRepository,
                currentActivityIntentRepository,
                CLOCK
        );
    }

    @Test
    void replacesIntentWithServerManagedTwoHourExpiry() {
        User user = User.builder().id(USER_ID).build();
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(currentActivityIntentRepository.findByUser_Id(USER_ID)).thenReturn(Optional.empty());
        when(currentActivityIntentRepository.save(any(UserCurrentActivityIntent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserCurrentActivityIntent result = currentActivityIntentService.replace(USER_ID, CurrentActivityIntent.CAFE);

        assertThat(result.getIntent()).isEqualTo(CurrentActivityIntent.CAFE);
        assertThat(result.getExpiresAt()).isEqualTo(LocalDateTime.of(2026, 8, 1, 12, 0));
    }

    @Test
    void returnsNullForExpiredIntentWithoutDeletingItDuringRead() {
        User user = User.builder().id(USER_ID).build();
        UserCurrentActivityIntent expiredIntent = UserCurrentActivityIntent.create(
                user,
                CurrentActivityIntent.EAT,
                LocalDateTime.of(2026, 8, 1, 10, 0)
        );
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(currentActivityIntentRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(expiredIntent));

        UserCurrentActivityIntent result = currentActivityIntentService.getCurrentIntent(USER_ID);

        assertThat(result).isNull();
        verify(currentActivityIntentRepository, never()).delete(expiredIntent);
    }
}
