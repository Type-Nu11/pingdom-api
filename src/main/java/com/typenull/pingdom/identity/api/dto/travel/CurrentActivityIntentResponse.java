package com.typenull.pingdom.identity.api.dto.travel;

import com.typenull.pingdom.identity.domain.travel.CurrentActivityIntent;
import com.typenull.pingdom.identity.domain.travel.UserCurrentActivityIntent;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "현재 행동 의도 응답")
public record CurrentActivityIntentResponse(
        @Schema(description = "현재 행동 의도. 설정되지 않았거나 만료됐으면 null", nullable = true, example = "CAFE")
        CurrentActivityIntent intent,
        @Schema(description = "행동 의도 만료 시각. 의도가 없으면 null", nullable = true)
        LocalDateTime expiresAt
) {

    public static CurrentActivityIntentResponse from(UserCurrentActivityIntent activityIntent) {
        if (activityIntent == null) {
            return new CurrentActivityIntentResponse(null, null);
        }
        return new CurrentActivityIntentResponse(activityIntent.getIntent(), activityIntent.getExpiresAt());
    }
}
