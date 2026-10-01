# 네이버 Directions 운영 검증

이 문서는 #1756의 `POST /routes` 503을 설정·공급자·앱 API 단계로 분리해 확인하는 절차다. 키를 생성·변경하거나 운영 배포를 수행하지 않는다.

## 원인 판별

`ROUTE_PROVIDER_UNAVAILABLE`은 다음을 하나의 공개 오류로 정규화한다. 서버 로그와 컨테이너 환경 상태로만 구분하며 키, JWT, 실제 사용자 좌표는 출력·이슈·PR에 기록하지 않는다.

| 확인 결과 | 원인 분류 | 조치 |
| --- | --- | --- |
| `NAVER_DIRECTIONS_ENABLED`이 true가 아님 | 기능 비활성 | 승인된 배포 환경 파일에서 true로 설정 후 컨테이너 재생성 |
| Client ID 또는 Secret이 비어 있음 | 인증정보 미설정 | Directions 5 권한이 있는 서버 전용 키를 환경 파일에 등록 |
| 공급자 검증이 401/403 | 인증 또는 상품 권한 | Naver Cloud Console의 Directions 5 사용 설정·키 권한·호스트 확인 |
| 공급자 검증이 429 | 쿼터 또는 과금 한도 | 계정 쿼터·과금 상태 확인 |
| 공급자 검증이 5xx/연결 실패 | 공급자 또는 네트워크 장애 | 상태·응답 시간만 기록하고 재시도 정책과 네트워크 확인 |
| 공급자는 성공, 앱 API만 실패 | 앱 배포 설정 또는 API 경로 | 실행 컨테이너 환경·배포 이미지·인증된 `/routes` 요청 확인 |

실행 중인 컨테이너에서는 값 대신 존재 여부만 확인한다.

```sh
docker compose exec -T app sh -c '
case "${NAVER_DIRECTIONS_ENABLED:-false}" in
  true) echo "naver_directions_enabled=true" ;;
  *) echo "naver_directions_enabled=false_or_invalid" ;;
esac
for name in NAVER_DIRECTIONS_CLIENT_ID NAVER_DIRECTIONS_CLIENT_SECRET; do
  eval "value=\${$name:-}"
  if [ -n "$(printf "%s" "$value" | tr -d "[:space:]")" ]; then
    echo "$name=present"
  else
    echo "$name=absent_or_blank"
  fi
done
'
```

## 실제 연동 검증

테스트 환경에 아래 값을 안전한 환경변수로 주입한다. `PINGDOM_ACCESS_TOKEN`은 운영에서 발급된 짧은 수명의 유효 access token만 사용한다. 값은 명령행 인자나 로그에 넣지 않는다.

```sh
NAVER_DIRECTIONS_ENABLED=true
NAVER_DIRECTIONS_CLIENT_ID=<server-client-id>
NAVER_DIRECTIONS_CLIENT_SECRET=<server-client-secret>
NAVER_DIRECTIONS_BASE_URL=https://maps.apigw.ntruss.com
PINGDOM_APP_BASE_URL=https://www.typenull.xyz
PINGDOM_ACCESS_TOKEN=<temporary-access-token>
./scripts/verify-naver-directions-api.sh
```

스크립트는 서울 도심→강남, 잠실→여의도 두 경로에 대해 다음을 확인한다.

- Directions 5: HTTP 200, `code=0`, 거리(m), 시간(ms), 경로점 2개 이상
- Pingdom: HTTP 200, `distanceMeters`, `durationSeconds`, `path` 2개 이상

키가 없으면 종료 코드 `2`와 `UNVERIFIED`를 반환한다. 앱 URL 또는 JWT가 없으면 공급자 검증만 수행하고 앱 검증은 `UNVERIFIED`로 기록한다. 공급자 또는 앱 검증 실패는 종료 코드 `1`이다.

## 활성화와 복구

1. Directions 5 상품 이용 권한, 키 권한, 쿼터·과금 상한을 확인한다.
2. 배포 환경 파일에 `NAVER_DIRECTIONS_ENABLED=true`, `NAVER_DIRECTIONS_CLIENT_ID`, `NAVER_DIRECTIONS_CLIENT_SECRET`을 설정한다.
3. 승인된 release 배포 절차로 컨테이너를 재생성하고, 위 스크립트로 공급자와 인증된 앱 API를 모두 확인한다.
4. 실패하면 `NAVER_DIRECTIONS_ENABLED=false`로 되돌리고 컨테이너를 재생성한다. 이때 `/routes`가 503이고 공급자 호출이 없음을 확인한다.

공급자 성공은 앱 UI 렌더링 성공을 대신하지 않는다. 성공 검증 후 앱에서 경로선·거리·시간이 표시되는지는 앱 이슈 #381에서 별도로 확인한다.
