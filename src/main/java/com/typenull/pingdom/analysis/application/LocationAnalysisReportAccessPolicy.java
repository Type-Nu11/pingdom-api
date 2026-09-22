package com.typenull.pingdom.analysis.application;

import com.typenull.pingdom.analysis.domain.exception.AnalysisReportErrorCode;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 요청 이메일을 공백 제거·소문자로 정규화하여 로그인 사용자의 현재 계정 이메일과 비교합니다.
 * 보고서 보관 서비스에 넘길 조회 키를 반환하며, 계정의 정지·탈퇴 상태를 별도로 판단하지는 않습니다.
 */
@Component
@RequiredArgsConstructor
public class LocationAnalysisReportAccessPolicy {

    private final UserRepository userRepository;

    /**
     * 사용자 ID·계정이 없거나 정규화한 요청 이메일이 계정 이메일과 다르면 보고서 접근을 거부한다.
     * 일치하면 조회·보관 키로 사용할 계정 이메일을 반환한다. 별도의 소유권 레코드를 생성하지 않는다.
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
