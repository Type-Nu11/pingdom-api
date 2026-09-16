# 좌표 기반 지역 핫플 장애 확인

대상: `GET /places/local-hot?latitude={latitude}&longitude={longitude}&page=1&limit=20`

## 설정

배포 Compose의 `app` 서비스는 서버 체크아웃의 `.env`를 읽는다.
좌표 기반 지역 판정에는 다음 두 설정이 모두 필요하다.

- `KAKAO_LOCAL_REGION_ENABLED=true` (기본값 `false`)
- `KAKAO_REST_API_KEY`: 서버용 Kakao REST API 키. 실제 값은 비밀 환경 설정으로 관리한다.

지도 표시용 클라이언트 키와 서버 REST API 키 주입은 별개다.
`.env`를 수정한 뒤에는 실행 중인 컨테이너를 재생성해야 반영된다.
변경 전 설정과 배포 이미지를 기록하고, 승인된 배포 절차로 반영한다.
기능 비활성화로 되돌리면 좌표 조회가 다시 503을 반환한다.

## 진단 순서

1. 재현 대상 API 주소, 배포 이미지, 요청 시간과 요청 ID를 확인한다.
2. 인증된 요청의 실제 HTTP 상태와 응답 `code`를 확인하고 서버 로그와 대조한다.
3. 해당 서버에서 아래 명령으로 실행 중인 컨테이너의 설정 상태만 확인한다.
   키 원문, 전체 환경 변수, 인증 헤더는 출력하거나 이슈에 첨부하지 않는다.

```sh
docker compose exec -T app sh -c '
case "${KAKAO_LOCAL_REGION_ENABLED:-false}" in
  true) echo "region_enabled=true" ;;
  *) echo "region_enabled=false_or_invalid" ;;
esac
if [ -n "$(printf "%s" "${KAKAO_REST_API_KEY:-}" | tr -d "[:space:]")" ]; then
  echo "rest_api_key=present"
else
  echo "rest_api_key=absent_or_blank"
fi
'
```

Spring 설정이 다른 경로에서 덮어써지는 환경에서는 해당 설정도 확인한다.
키 존재 여부만으로 키의 유효성이나 외부 API 접근 가능성을 판정할 수는 없다.

| 응답 | 의미와 다음 확인 |
| --- | --- |
| 503 `LOCAL_HOT_REGION_RESOLUTION_UNAVAILABLE` | 기능 비활성화 또는 키 누락. 설정 주입과 실행 컨테이너 반영 여부 확인 |
| 502 `LOCAL_HOT_REGION_RESOLUTION_FAILED` | 외부 호출 실패. Kakao 인증·권한·쿼터와 DNS·네트워크·타임아웃 확인 |
| 404 `LOCAL_HOT_REGION_NOT_FOUND` | 좌표에서 필요한 법정동 지역 정보를 얻지 못했거나, 전달한 지역 코드가 DB에 없음 |
| 200, `places: []` | 지역 판정은 성공했지만 조회 가능한 장소가 없음. 지역 매핑과 공개·운영 상태 확인 |

현재 Resolver는 외부 호출 예외의 상세 원인을 응답에 포함하지 않는다.
502만으로 인증 실패·쿼터 초과·네트워크 장애 중 하나를 확정하지 않는다.

## 복구 확인

- 유효한 국내 좌표로 인증된 요청을 보내 200과 올바른 시·군·구를 확인한다.
- 해당 지역에 장소가 없어도 200과 빈 목록이 반환되는지 확인한다.
- 테스트 환경에서 설정 누락은 503, 외부 호출 실패는 502로 유지되는지 확인한다.
- 전국 트렌드 카테고리 문제와 지역 데이터 백필은 별도 범위로 처리한다.
