package com.typenull.pingdom.moderation.api.appeal;

import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.moderation.api.dto.appeal.ReportAppealCreateRequest;
import com.typenull.pingdom.moderation.api.dto.appeal.ReportAppealCreateResponse;
import com.typenull.pingdom.moderation.application.service.appeal.ReportAppealService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/map/report-appeals")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class ReportAppealController {

    private final ReportAppealService reportAppealService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "신고 처리 이의제기 요청",
            description = "신고 대상 사용자가 신고 처리 또는 자동 숨김에 대해 이의제기를 제출합니다."
    )
    public ReportAppealCreateResponse submit(
            @Valid @RequestBody ReportAppealCreateRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        if (user == null) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
        return reportAppealService.submit(request.reportId(), request.reason(), user.userId(), user.username());
    }
}
