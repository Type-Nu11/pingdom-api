package com.typenull.pingdom.analysis.application;

import com.typenull.pingdom.analysis.domain.exception.AnalysisReportErrorCode;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LocationAnalysisReportAccessPolicy {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public String requireOwnedEmail(Long userId, String requestedEmail) {
        if (userId == null) {
            throw forbidden();
        }
        String accountEmail = userRepository.findById(userId)
                .map(user -> normalizeEmail(user.getEmail()))
                .orElseThrow(this::forbidden);

        if (!accountEmail.equals(normalizeEmail(requestedEmail))) {
            throw forbidden();
        }
        return accountEmail;
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private AnalysisReportException forbidden() {
        return new AnalysisReportException(AnalysisReportErrorCode.ANALYSIS_REPORT_FORBIDDEN, null);
    }
}
