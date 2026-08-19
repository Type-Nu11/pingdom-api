package com.typenull.pingdom.integration.migration.fixture;

import java.util.List;

public final class FlywayBackfillFixtures {

    private FlywayBackfillFixtures() {
    }

    public static List<FlywayBackfillScenario> scenarios() {
        return List.of(
                new FlywayBackfillScenario(
                        "legacy-place-image-to-exploration-media",
                        "56",
                        "place_media",
                        FlywayBackfillScenarioType.NORMAL,
                        "map_place.image_url이 존재하는 기존 장소",
                        "purpose=EXPLORATION, display_order=0인 media 1건",
                        null,
                        List.of("기존 장소 보존", "image_url backfill", "place_media 생성")
                ),
                new FlywayBackfillScenario(
                        "legacy-map-image-to-verification-media",
                        "56",
                        "place_media",
                        FlywayBackfillScenarioType.BOUNDARY,
                        "map_image의 image_url과 created_time이 존재하는 기존 검증 이미지",
                        "source_map_image_id 연결과 created_at 보존",
                        null,
                        List.of("원본 이미지 연결", "created_time 보존", "중복 source 방지")
                ),
                new FlywayBackfillScenario(
                        "legacy-place-without-image-is-skipped",
                        "56",
                        "place_media",
                        FlywayBackfillScenarioType.BOUNDARY,
                        "image_url이 NULL이거나 공백인 기존 장소",
                        "place_media 미생성, 장소 row 보존",
                        null,
                        List.of("NULL 입력 경계", "공백 입력 경계", "불필요한 row 미생성")
                ),
                new FlywayBackfillScenario(
                        "legacy-claim-defaults-to-initial",
                        "42",
                        "merchant_place_claim",
                        FlywayBackfillScenarioType.NORMAL,
                        "기존 merchant_place_claim row에 claim_type이 없는 상태",
                        "claim_type=INITIAL, previous_owner_user_id=NULL",
                        null,
                        List.of("기존 claim 보존", "INITIAL 기본값", "소유권 제약 적용 순서")
                ),
                new FlywayBackfillScenario(
                        "ownership-transfer-invalid-legacy-row",
                        "43",
                        "merchant_place_claim",
                        FlywayBackfillScenarioType.FAILURE,
                        "INITIAL claim인데 previous_owner_user_id가 채워진 기존 row",
                        "마이그레이션 중단, 잘못된 데이터 보존 상태 확인",
                        "ownership transfer constraint violation",
                        List.of("실패 migration 식별", "제약 조건 위반 원인", "부분 적용 방지")
                ),
                new FlywayBackfillScenario(
                        "legacy-place-information-defaults",
                        "54",
                        "map_place",
                        FlywayBackfillScenarioType.RETRY,
                        "기존 장소에 출처·검증 상태 컬럼이 없는 상태",
                        "primary_information_source=LEGACY, information_verification_status=UNVERIFIED",
                        null,
                        List.of("동일 migration 재실행", "기존 기본값 유지", "중복 column 방지")
                )
        );
    }
}
