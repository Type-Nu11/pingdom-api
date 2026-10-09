# 네이버 Directions 운영 검증

이 문서는 #1756의 `POST /routes` 503을 설정·공급자·앱 API 단계로 분리해 확인하는 절차다. 키를 생성·변경하거나 운영 배포를 수행하지 않는다.

## #1765 운영 조사 결과 (2026-10-08 KST)

실제 운영 서버를 조회해 아래 내용을 확인했다. 아래는 설정 반영 전 조사 결과이며, 이후 반영 결과는 별도 절에 기록한다.

- 2026-10-07 04:55~05:00 UTC 로그 구간(앱 재현 04:57:01 UTC 포함)에서 다음 실패 분류를 확인했다. 요청별 trace ID 일치는 별도 확인이 필요하다.

  ```text
  Route provider failure provider=naver outcome=configuration enabled=false clientIdPresent=false clientSecretPresent=false
  ```

- 실행 중인 `pingdom-app-1`의 `NAVER_DIRECTIONS_ENABLED`, `NAVER_DIRECTIONS_CLIENT_ID`, `NAVER_DIRECTIONS_CLIENT_SECRET` 환경변수가 모두 없었다.
- 배포 디렉터리 `/home/ubuntu/Pingdom_Backend/.env`에도 Directions 관련 항목이 없었다.
- `compose.yaml`의 app 서비스는 `PINGDOM_ENV_FILE`(기본 `.env`)을 `env_file`로 주입한다. 코드의 기본값은 기능 비활성·빈 키이므로 공급자 호출 전에 503을 반환한다.

따라서 확인된 실패는 **운영 설정 누락**이다. 해당 요청에서 공급자 인증·쿼터·네트워크 실패가 발생했다고 판단할 근거는 없다. Java 기본값을 강제로 활성화하거나 다른 네이버 상품의 키를 재사용하는 것으로 해결하지 않는다.

### 남은 복구와 완료 조건

1. Directions 5 권한이 있는 서버 전용 키를 확보해 실제 배포 환경 파일에 등록한다. 키 원문은 Git·명령행 인자·로그에 넣지 않는다.
2. `NAVER_DIRECTIONS_ENABLED=true`를 설정하고 배포에 사용하는 동일한 `PINGDOM_ENV_FILE`과 이미지로 app 컨테이너를 재생성한다. 단순 `restart`는 변경한 환경변수를 반영하지 않는다.
3. 아래 존재 여부 확인 명령으로 활성화·키 주입을 확인하고 공급자 응답을 검증한다.
4. 로그인된 실제 Android 앱에서 외부 HTTPS `POST /routes`의 200, `mode=car`, `provider=naver`, 서로 다른 유효 경로 좌표 2개 이상, 거리(m)·시간(초), 경로선 표시를 확인한다. 프록시 인증 정책도 포함하므로 JWT만 사용하는 직접 요청 성공으로 앱 검증을 대체하지 않는다.

### 운영 설정 반영 결과 (2026-10-08 KST)

- 전달받은 키로 Directions 5 HTTP 200·`code=0`을 확인한 후 운영 `.env`에 `NAVER_DIRECTIONS_ENABLED=true`와 Directions 전용 변수명의 Client ID/Secret을 등록했다. 키 원문은 저장소에 기록하지 않았다.
- 기존 `.env`는 배포 디렉터리 밖의 접근 제한된 백업 파일로 보관했다. 기존 이미지 `735cfe6920c497564529101ff937ab62af02b9b8`와 포트 바인딩을 유지하고 app만 `--no-deps --force-recreate`로 재생성했다.
- 재생성된 컨테이너에서 `enabled=true`, Client ID/Secret 존재 여부를 확인했고 `/actuator/health/readiness`는 `UP`이었다.
- 운영 호스트에서 컨테이너에 주입된 키로 공급자를 직접 호출한 결과다. 응답 시간·교통 상황에 따라 거리·소요시간은 달라질 수 있다.

  | 경로 | HTTP / code | 서로 다른 경로점 | 거리(m) | 시간(ms) |
  | --- | --- | --- | --- | --- |
  | 서울시청 → 강남 | 200 / 0 | 313 | 9964 | 1539739 |
  | 잠실 → 여의도 | 200 / 0 | 458 | 22333 | 2488677 |

설정 등록·app 재생성·공급자 호출 검증은 완료했다. **외부 HTTPS의 인증된 `POST /routes` 및 실제 Android 앱의 경로선·거리·시간 표시 검증은 미완료**다. 공급자 직접 호출과 readiness 성공으로 #1765를 종료하지 않는다.

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
