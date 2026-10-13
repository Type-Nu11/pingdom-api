# 업체명·주소 검색 환경 설정

관련 이슈: [#1739](https://github.com/Type-Nu11/pingdom-api/issues/1739)

업체명 검색은 **NAVER API HUB의 지역 검색**, 주소 검색은 **Maps의 Geocoding**을 사용한다.
각 Application에서 해당 API를 신청·선택하고 발급한 Client ID와 Client Secret을 사용한다.
헤더 이름이 같아도 서비스의 키를 서로 대체할 수 있다고 가정하지 않는다.
NAVER Developers의 검색 키는 API HUB 클라이언트에서 사용하지 않는다.

## 운영 환경 변수

실제 값은 운영 환경 파일에만 입력하고 Git이나 로그에 저장하지 않는다.
아래 `<...>`는 실제 값으로 교체해야 하는 설명용 자리표시자다.

```dotenv
NAVER_PLACE_SEARCH_ENABLED=true
NAVER_PLACE_SEARCH_CLIENT_ID=<API HUB 지역 검색 Client ID>
NAVER_PLACE_SEARCH_CLIENT_SECRET=<API HUB 지역 검색 Client Secret>
NAVER_PLACE_SEARCH_BASE_URL=https://naverapihub.apigw.ntruss.com

NAVER_ADDRESS_SEARCH_ENABLED=true
NAVER_ADDRESS_SEARCH_CLIENT_ID=<Maps Geocoding Client ID>
NAVER_ADDRESS_SEARCH_CLIENT_SECRET=<Maps Geocoding Client Secret>
NAVER_ADDRESS_SEARCH_BASE_URL=https://maps.apigw.ntruss.com
```

두 `BASE_URL`은 새 코드의 기본값과 같으므로 생략할 수 있다. 기존 환경 파일에 구 URL이
명시되어 있으면 새 URL로 수정하거나 해당 override를 제거해야 한다.
한 서비스의 인증값만 확보했다면 그 서비스만 활성화한다.
비활성 상태 또는 빈 인증값은 외부 호출 전에 503으로 처리한다.

## 반영·검증 순서

1. API HUB 요청 경로 `/search/v1/local`과 NCP 인증 헤더를 사용하는 새 이미지를 배포한다.
   구 업체명 클라이언트는 `/v1/search/local.json`과 NAVER Developers 헤더를 사용하므로
   환경 변수만 바꾸어서는 API HUB 키로 호출할 수 없다.
2. Compose가 참조하는 운영 환경 파일을 확인하고, 기존 파일 권한을 유지하며 검색 설정을 적용한다.
   적용 전 환경 파일과 현재 이미지 식별자를 접근 제한된 위치에 보관한다.
3. 애플리케이션 컨테이너를 재생성해 환경 변수를 반영한다. 단순 restart는 변경된 환경 변수를 반영하지 않는다.
4. 컨테이너의 활성화 값과 인증값 존재 여부만 확인한다. 환경 파일 전체나 `docker inspect`의 전체 Env를 출력하지 않는다.
5. 유효한 사용자 인증으로 두 검색 API의 상태와 검색 후보를 확인한다.
   readiness 200은 외부 검색 성공을 입증하지 않는다.
6. 업체명 응답의 `mapx`·`mapy` 표현과 반환 위경도를 확인한다.
   현재 변환은 기존 정수 좌표를 1천만으로 나누며, 공식 API HUB 문서는 WGS84만 명시하고 배율은 명시하지 않는다.
   실제 응답 형식 확인 전 좌표 호환성이 검증되었다고 보고하지 않는다.

문제가 생기면 기존 이미지와 보관한 검색 설정을 함께 복원한다.
업체명 외부 호출 실패는 기존대로 502로 처리한다. 이 변경은 사용자 인증·검색 응답 DTO·오류 계약을 변경하지 않는다.

## 검증 결과 — 2026-10-13

- Java 21에서 업체명·주소 검색 클라이언트/서비스와 두 전용 클라이언트의 구성 테스트 21개가 통과했다.
- 사용자 제공 API HUB 인증값으로 공식 지역 검색 endpoint를 로컬에서 직접 호출해 HTTP 200과 후보 1건을 확인했다.
  해당 응답의 좌표는 1천만 배 정수 표현으로, 기존 좌표 변환에 맞는 형식이었다.
- 같은 인증값으로 Maps Geocoding을 호출하면 HTTP 401, 오류 코드 210(권한 없음)이 반환되었다.
  주소 검색에 이 키를 재사용하지 않는다.
- 운영 환경 파일에 이미 존재하던 Maps Directions 인증값은 Geocoding 직접 호출에서
  HTTP 200, `status=OK`, 주소 후보 1건을 반환했다. 사용자 요청에 따라 이번 운영 설정은 업체명 검색부터 준비한다.
- 운영 `.env`에 API HUB Client ID·Secret과 base URL을 저장하고, 원본은 저장소 외부의 권한 제한된 위치에 백업했다.
  파일 권한은 0600이며 업체명 검색 외 기존 설정은 유지했다.
  저장된 설정으로 운영 서버에서 API HUB를 직접 호출해 HTTP 200과 후보 1건을 확인했다.
  현재 운영 이미지 `0a7990096a08c8a0ae9660ccd1cb58ca42acfcaf`의 소스는 구 검색 경로를 사용하므로
  활성화 값은 `false`로 준비했다. 새 코드 배포 시 `true`로 변경하고 컨테이너를 재생성해야 한다.
  현재 애플리케이션 컨테이너는 재생성하지 않았으며 readiness는 HTTP 200이었다.
- 이 직접 호출은 운영 서버의 인증된 검색 API 성공이나 새 코드 배포를 입증하지 않는다.

## 공식 계약

- [NAVER API HUB 지역 검색](https://api.ncloud-docs.com/docs/naver-api-hub-search-local)
- [API HUB Application 등록](https://guide.ncloud-docs.com/docs/apihub-application)
- [Maps endpoint·인증 헤더](https://api.ncloud-docs.com/docs/application-maps-overview)
