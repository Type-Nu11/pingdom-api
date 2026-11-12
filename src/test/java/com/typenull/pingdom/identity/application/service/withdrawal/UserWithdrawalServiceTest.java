package com.typenull.pingdom.identity.application.service.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserStatus;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.privacy.application.PrivacyProcessingOutboxPublisher;
import com.typenull.pingdom.privacy.domain.PrivacyProcessingAction;
import com.typenull.pingdom.privacy.event.PrivacyProcessingEvent;
import com.typenull.pingdom.shared.security.access.UserAccessStatusService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserWithdrawalServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    @Mock private UserRepository users;
    @Mock private PrivacyProcessingOutboxPublisher privacy;
    @Mock private UserWithdrawalDataService data;
    @Mock private UserAccessStatusService access;
    private UserWithdrawalService service;

    @BeforeEach
    void setUp() {
        service = new UserWithdrawalService(users, privacy, data, access, CLOCK);
    }

    @Test
    void withdrawalKeepsAuditAnonymizationAndCleanupOrder() {
        User user = spy(User.builder().id(1L).username("user").email("user@example.com").build());
        user.issueRefreshToken("refresh");
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        service.withdraw(1L);

        InOrder ordered = inOrder(users, privacy, user, access, data);
        ordered.verify(users).findByIdForUpdate(1L);
        ordered.verify(privacy).publish(argThat((PrivacyProcessingEvent event) ->
                event.action() == PrivacyProcessingAction.WITHDRAWAL_REQUESTED && event.subjectUserId().equals(1L)));
        ordered.verify(user).withdraw(eq("withdrawn_user_1"), eq("withdrawn_user_1@withdrawn.local"),
                startsWith("WITHDRAWN_"), eq(NOW));
        ordered.verify(privacy).publish(argThat((PrivacyProcessingEvent event) ->
                event.action() == PrivacyProcessingAction.ANONYMIZED && event.subjectUserId().equals(1L)));
        ordered.verify(access).evict(1L);
        ordered.verify(data).cleanupUserOwnedData(1L);
        assertThat(user.isWithdrawn()).isTrue();
        assertThat(user.getRefreshToken()).isNull();
        assertThat(user.getEmail()).isEqualTo("withdrawn_user_1@withdrawn.local");
    }

    @Test
    void repeatedWithdrawalDoesNotDuplicateAuditOrCleanup() {
        User user = User.builder().id(1L).status(UserStatus.WITHDRAWN).build();
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        service.withdraw(1L);

        verifyNoInteractions(privacy, data, access);
    }

    @Test
    void cleanupFailurePropagatesToTheFacadeTransaction() {
        User user = User.builder().id(1L).username("user").email("user@example.com").build();
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        RuntimeException failure = new IllegalStateException("cleanup failed");
        doThrow(failure).when(data).cleanupUserOwnedData(1L);

        assertThatThrownBy(() -> service.withdraw(1L)).isSameAs(failure);
    }
}
