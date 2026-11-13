package com.typenull.pingdom.analysis.application;

import com.typenull.pingdom.analysis.domain.exception.AnalysisReportErrorCode;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 요청 이메일을 공백 제거·소문자로 정규화하여 로그인 사용자의 현재 계정 이메일과 비교.
 * 기존 API의 연락처 검증을 유지하며, 보고서 소유권은 별도로 생성 당시 사용자 ID로 제한.
 */
@Component
@RequiredArgsConstructor
public class LocationAnalysisReportAccessPolicy {

    private final UserRepository userRepository;

    /**
     * 사용자 ID·계정이 없거나 정규화한 요청 이메일이 계정 이메일과 다르면 보고서 접근 거부.
     * 일치하면 연락처로 사용할 계정 이메일 반환. 이 결과만으로 보고서 소유권을 판정하지 않음.
     */
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
