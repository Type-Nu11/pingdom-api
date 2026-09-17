# 네이버 행정구역 실제 연동 검증

이 문서는 #1676의 실제 네이버 Reverse Geocoding 검증 절차다. 자동 단위 테스트와 실제 외부 API
검증, 운영 배포는 서로 별개로 기록한다. 이 절차는 키를 생성·변경하거나 backfill을 실행하지 않는다.

## 사전 조건

- 테스트 환경에 `NAVER_MAP_CLIENT_ID`, `NAVER_MAP_CLIENT_SECRET`이 비밀 환경변수로 준비되어 있어야 한다.
- 애플리케이션 캐시까지 확인할 때만 `PINGDOM_APP_BASE_URL`, `PINGDOM_ACCESS_TOKEN`을 같은 테스트 환경에 준비한다.
- 값은 명령행 인자, 로그, 이슈, PR 본문에 기록하지 않는다.

키가 없으면 아래 스크립트는 종료 코드 `2`와 `UNVERIFIED`를 반환한다. 이 경우 실제 연동은 미검증으로
기록하며, 키 변경·운영 배포·backfill 실행을 진행하지 않는다.

```bash
./scripts/verify-naver-local-region-api.sh
```

## 검증 항목

스크립트는 네이버 API에 대표 좌표를 요청하고 키를 제외한 결과만 출력한다.

| 대상 | 기대 지역 코드 | 확인 내용 |
| --- | --- | --- |
| 서울 | `11680` | 일반 시·군·구 매핑 |
| 광양 | `46230` | 일반 시 지역 매핑 |
| 세종 | `36110` | 빈 `area2` 특수 처리 |

성공 시 HTTP 상태, 네이버 내부 `status`, 앞 5자리 지역 코드, 응답 시간만 확인한다. 앱 URL과 테스트
토큰도 준비된 경우 같은 좌표를 두 번 조회해 각 요청의 상태·응답 시간만 출력한다. 두 번째 요청의 지연 시간은
캐시 효과를 판단하는 참고값이며, 네트워크 상태에 따라 단독 통과 기준으로 사용하지 않는다.

## 기능 토글 및 복구

테스트 환경에서만 다음 순서로 확인한다.

1. `NAVER_LOCAL_REGION_ENABLED=false`로 컨테이너를 재생성하고 좌표 조회가 `503 LOCAL_HOT_REGION_RESOLUTION_UNAVAILABLE`인지 확인한다.
2. Client ID·Secret을 유지한 채 `NAVER_LOCAL_REGION_ENABLED=true`로 재생성하고 대표 좌표가 200인지 확인한다.
3. 실패하면 `NAVER_LOCAL_REGION_ENABLED=false`로 되돌리고, 키 원문 대신 HTTP 상태·오류 코드·요청 시간을 기록한다.

운영 환경의 설정 변경과 컨테이너 재생성은 승인된 배포 절차에서만 수행한다. `LOCAL_HOT_REGION_BACKFILL_ENABLED`
기본값은 `false`이며, 이 검증 절차에서 활성화하지 않는다.
