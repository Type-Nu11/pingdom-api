# 메뉴 환산 불가·기본 통화 응답

## 적용 범위

관광객용 `GET /places/{placeId}/menus`는 메뉴의 원래 가격과 통화를 항상 반환한다.
`convertedPrice`는 참고용 정보이므로 환산할 수 없더라도 메뉴 목록 요청을 실패시키지
않는다.

## 기본 표시 통화

| 요청 사용자 상태 | 표시 통화 |
| --- | --- |
| 비로그인 | `KRW` |
| 국가 값이 없거나 `UNKNOWN` | `KRW` |
| 지원하지 않는 국가 코드 | `KRW` |
| 지원 국가 코드 | 국가 매핑에 따른 `KRW`, `USD`, `JPY`, `CNY`, `EUR` |

기본 통화가 메뉴 원래 통화와 같으면 외부 환율 API를 호출하지 않으며
`convertedPrice`는 `null`이다.

```json
{
  "priceAmount": 9000,
  "currency": "KRW",
  "convertedPrice": null
}
```

## 환산 불가 응답

아래 상황에서는 원래 가격·통화를 유지하고 `convertedPrice`만 `null`로 반환한다.

- 외부 환율 API가 비활성화됐을 때
- 연결·읽기 시간 초과 또는 HTTP 오류가 발생했을 때
- 환율 또는 기준 일자 응답이 누락되었거나 형식이 올바르지 않을 때
- 통화 쌍의 환율을 찾지 못했을 때

예를 들어 미국 국가 코드 사용자가 `KRW` 메뉴를 조회했지만 환율 API를 사용할 수
없는 경우에도 HTTP 200으로 다음처럼 반환한다.

```json
{
  "priceAmount": 9000,
  "currency": "KRW",
  "convertedPrice": null
}
```

`convertedPrice`가 `null`인 것은 환산 참고 정보를 제공하지 못했다는 뜻이며, 메뉴
자체가 없거나 조회 권한이 없다는 오류가 아니다. 클라이언트는 이 경우 원래
`priceAmount`와 `currency`를 표시해야 한다.

## 기준 시각과 이전 환율

환산에 성공한 경우에만 `convertedPrice.rateDate`에 외부 제공 환율의 기준 일자를
반환한다. 현재 정책은 캐시 TTL 10분이 지난 이전 환율을 재사용하지 않으며, 만료 후
새 조회가 실패하면 위 환산 불가 응답을 적용한다.

## 구현 대조

- `MenuDisplayCurrencyResolver`가 비로그인·미설정·미지원 국가의 기본값 `KRW`를 결정한다.
- `FrankfurterCurrencyExchangeRateClient`는 외부 호출·응답 파싱 실패를 빈 결과로 전환한다.
- `MenuPriceConversionService`는 빈 환율 결과를 `null` 환산 가격으로 변환한다.
- `PlaceMenuPublicResponse`는 원래 가격·통화와 nullable `convertedPrice`를 함께 반환한다.

## 변경 이력

| 일자 | 이슈 | 내용 |
| --- | --- | --- |
| 2026-09-12 | #1582 | 기본 통화와 환산 불가 시 원래 가격 유지 응답을 문서화 |
