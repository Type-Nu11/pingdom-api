package com.typenull.pingdom.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.analysis.domain.exception.AnalysisReportErrorCode;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LocationAnalysisReportAccessPolicyTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final LocationAnalysisReportAccessPolicy accessPolicy =
            new LocationAnalysisReportAccessPolicy(userRepository);

    @Test
    void returnsCanonicalAccountEmailWhenRequestedEmailMatches() {
        User user = mock(User.class);
        when(user.getEmail()).thenReturn("Owner@Example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        String ownedEmail = accessPolicy.requireOwnedEmail(1L, " owner@example.com ");

        assertThat(ownedEmail).isEqualTo("owner@example.com");
    }

    @Test
    void rejectsEmailThatDoesNotBelongToAuthenticatedUser() {
        User user = mock(User.class);
        when(user.getEmail()).thenReturn("owner@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> accessPolicy.requireOwnedEmail(1L, "other@example.com"))
                .isInstanceOfSatisfying(AnalysisReportException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(AnalysisReportErrorCode.ANALYSIS_REPORT_FORBIDDEN));
    }

    @Test
    void rejectsMissingAuthenticatedUser() {
        assertThatThrownBy(() -> accessPolicy.requireOwnedEmail(null, "owner@example.com"))
                .isInstanceOfSatisfying(AnalysisReportException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(AnalysisReportErrorCode.ANALYSIS_REPORT_FORBIDDEN));
    }
}
