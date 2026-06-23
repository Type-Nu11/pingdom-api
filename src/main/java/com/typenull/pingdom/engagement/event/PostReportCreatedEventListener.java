package com.typenull.pingdom.engagement.event;

import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostReportCreatedEventListener {

    private final PostReportRepository postReportRepository;
    private final UserRepository userRepository;

    @EventListener
    public void handle(PostReportCreatedEvent event) {
        Long postReportId = event.postReportId();

        PostReport report = postReportRepository.findById(postReportId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.REPORT_NOT_FOUND));

        User reporter = userRepository.findById(report.getReporterUserId())
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        if (reporter.getUnacceptedReportCount() >= 5 && reporter.getUnacceptedReportPercent() >= 90){
            report.increaseReportScore(10);
        }

    }
}