# #1771 장소명·등록 메뉴 통합 검색

기존 `GET /places`의 keyword 검색에 공개 메뉴명을 추가한다. 기존 이름·영문명·주소 검색, DTO, 인증, 페이지 응답 형식을 유지한다.

```http
GET /places?keyword=갈비탕&category=RESTAURANT&latitude=37.5&longitude=127.0&radiusKm=3&sort=NEAREST&limit=3
```

## 검색 계약

- 장소명·영문명·주소(일반/도로명/지번) **또는** 공개 메뉴명 중 하나가 부분 일치하면 후보다. 메뉴 정보는 `place_menu.name`에 저장된 값만 사용한다.
- 기존 `LOWER(...) LIKE` 패턴을 확장한다. 영문 대소문자를 구분하지 않으며 ILIKE와 동일한 부분 검색 목적이다. 앞뒤 공백을 제거하고 null·빈 문자열·공백만 있는 keyword는 검색어 필터를 생략한다. 중간 공백은 그대로 비교한다.
- `%`, `_`, 역슬래시는 와일드카드가 아닌 문자 그대로 검색한다. 메뉴 설명·가격·이미지 URL은 검색하지 않는다.
- 기존 공개 메뉴 목록과 동일하게 AVAILABLE/SOLD_OUT 메뉴를 검색한다. SOLD_OUT 검색 결과는 현재 주문 가능함을 보장하지 않는다. HIDDEN/INACTIVE 메뉴는 제외한다. 메뉴 미등록 장소도 이름·주소가 일치하면 검색된다.
- category/touristCategory는 선택 AND 필터다. 음식점 카테고리만으로 keyword와 무관한 음식점이 포함되지 않는다. category는 기존 정책에 따라 한글·영문 별칭을 표준 값으로 정규화한다.
- 메뉴 조건은 EXISTS로 적용해 이름과 여러 메뉴가 동시에 일치해도 장소와 totalCount는 중복되지 않는다. 목록 SQL과 count SQL에 동일한 조건을 적용한다.
- 공개·운영 중인 장소의 위치/반경과 검색 조건을 적용한 뒤 정렬·페이지 제한을 적용한다. 주변 거리순 조회는 latitude/longitude/radiusKm와 sort=NEAREST를 사용한다. 기본 정렬 LATEST는 유지한다. limit는 기존 1~100이며 앱은 3을 전달할 수 있다.
- keyword/category를 생략하면 기존 일반 주변 탐색을 지원한다. 예약 날짜·시간·인원은 필요하지 않다. 결과 없음은 200, places=[], totalCount=0이다.
- 자동완성·예약 검색·AI 도구 호출 계약(#1770)은 이 변경 범위에 포함하지 않는다.

## 구현 흐름

Controller의 검색 조건 → Service의 공백·LIKE 문자 이스케이프 및 카테고리 정규화 → Repository의 장소명/주소 OR 공개 메뉴 EXISTS → 반경·정렬·페이지 → 기존 PlaceListResponse.

DB 스키마 변경은 없다. V125의 place_menu와 기존 `(place_id, status, display_order, place_menu_id)` 인덱스를 사용해 장소별 공개 메뉴 범위를 조회한다. 검색량 증가 시 실행 계획과 메뉴 데이터량을 측정해 추가 인덱스 필요성을 판단한다.

## 검증

실제 PostGIS에서 장소명/메뉴 OR, 메뉴 여러 건 일치의 페이지 총 건수, 공개 상태, 카테고리 조합, 숨김/폐업/반경 제외, 거리순 limit=3, 검색어 없는 탐색, 결과 없음, 영문 대소문자와 LIKE 특수문자를 검증한다.

운영 서버 배포와 실제 서비스 요청 검증은 아직 수행하지 않았다. 로컬 테스트 결과와 운영 검증을 구분한다.

최종 로컬 검증: 단위 테스트 1,269개, PostGIS 검색·API 테스트 7개, OpenAPI 계약 테스트 1개 및 bootJar 통과. `git diff --check` 통과. 목록과 count SQL의 조건 일치, 메뉴 노출 상태, LIKE 이스케이프, 기존 응답 호환성을 다시 검토했다.

확장 회귀 실행에서 H2 기반 PlaceControllerTest는 기존 PostGIS ST_DISTANCE 함수 미지원으로 12개 실패했다. 함께 선택된 관리자 테스트도 registrant 필수값·삭제 권한·중복 조회 응답 관련 4개 실패가 있었다. 해당 테스트 인프라/관리자 코드는 변경하지 않았으며, 전체 통합 테스트 통과로 보고하지 않는다. 이번 검색 계약은 실제 PostgreSQL/PostGIS와 인증된 MockMvc API 요청으로 검증했다.
