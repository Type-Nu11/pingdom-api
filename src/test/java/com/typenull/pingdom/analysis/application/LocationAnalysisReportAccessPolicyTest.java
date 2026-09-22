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

    /**
     * 계정 이메일과 요청 이메일이 대소문자·양끝 공백만 다르면 소문자 정규 이메일을 반환하는지 검증한다.
     */
    @Test
    void normalizesOwnedReportEmail() {
        User user = mock(User.class);
        when(user.getEmail()).thenReturn("Owner@Example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        String ownedEmail = accessPolicy.requireOwnedEmail(1L, " owner@example.com ");

        assertThat(ownedEmail).isEqualTo("owner@example.com");
    }

    /**
     * 계정과 다른 이메일로 보고서를 요청하면 ANALYSIS_REPORT_FORBIDDEN인지 검증한다.
     */
    @Test
    void rejectsUnownedReportEmail() {
        User user = mock(User.class);
        when(user.getEmail()).thenReturn("owner@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> accessPolicy.requireOwnedEmail(1L, "other@example.com"))
                .isInstanceOfSatisfying(AnalysisReportException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(AnalysisReportErrorCode.ANALYSIS_REPORT_FORBIDDEN));
    }

    /**
     * 인증 사용자 ID가 null이면 ANALYSIS_REPORT_FORBIDDEN으로 거절되는지 검증한다.
     */
    @Test
    void rejectsMissingAuthenticatedUser() {
        assertThatThrownBy(() -> accessPolicy.requireOwnedEmail(null, "owner@example.com"))
                .isInstanceOfSatisfying(AnalysisReportException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(AnalysisReportErrorCode.ANALYSIS_REPORT_FORBIDDEN));
    }
}
