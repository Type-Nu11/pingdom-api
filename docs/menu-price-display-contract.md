# 메뉴 환산 가격 표시 계약

## 금액 정밀도와 반올림

환산 금액은 `원래 가격 × 환율`을 계산한 뒤 표시 통화 기준으로 `HALF_UP` 반올림한다.

| 표시 통화 | 소수 자릿수 | 예시 |
| --- | ---: | --- |
| `KRW`, `JPY` | 0 | `1234.5` → `1235` |
| `USD`, `CNY`, `EUR` | 2 | `6.425` → `6.43` |

원래 메뉴 가격 `priceAmount`는 `long` 기본 단위로 저장·반환하며, 환산으로 인해
변경하지 않는다.

## 응답 계약

`GET /places/{placeId}/menus`의 각 메뉴 항목은 다음 계약을 따른다.

| 필드 | 의미 | 반환 조건 |
| --- | --- | --- |
| `priceAmount` | 원래 메뉴 가격 | 항상 반환 |
| `currency` | 원래 메뉴 통화 | 항상 반환 |
| `convertedPrice.amount` | 참고 환산 금액 | 환산에 성공하고 원래 통화와 표시 통화가 다를 때만 반환 |
| `convertedPrice.currency` | 표시 통화 | `convertedPrice`가 있을 때만 반환 |
| `convertedPrice.rateDate` | 외부 환율 기준 일자 | `convertedPrice`가 있을 때만 반환 |

정상 환산 응답 예시:

```json
{
  "priceAmount": 9000,
  "currency": "KRW",
  "convertedPrice": {
    "amount": 6.43,
    "currency": "USD",
    "rateDate": "2026-09-10"
  }
}
```

원래 통화와 표시 통화가 같거나, 환율 조회·파싱에 실패했거나, 환율 조회가 비활성화된
경우에는 `convertedPrice`가 `null`이다. 이 상태는 메뉴 조회 실패가 아니며, 클라이언트는
원래 가격과 통화를 계속 표시해야 한다.

## 표시 원칙

- 환산 가격은 참고용이므로 결제 금액·정산 금액·원래 가격을 대체하지 않는다.
- `rateDate`는 환율의 기준 일자이며, 응답 생성 시각 또는 캐시 만료 시각이 아니다.
- 환산 불가 상태를 별도 오류 코드로 반환하지 않는다. 메뉴 목록은 정상 응답하고 원래 가격을 보존한다.

## 구현 대조

`MenuPriceConversionService`가 통화별 `setScale`과 `RoundingMode.HALF_UP`을 적용하며,
`PlaceMenuPublicResponse`가 원래 가격·통화와 nullable `convertedPrice`를 함께 반환한다.

## 변경 이력

| 일자 | 이슈 | 내용 |
| --- | --- | --- |
| 2026-09-12 | #1564 | 통화별 정밀도, 반올림, 환산 가격·기준 일자·실패 상태 응답 계약을 확정 |
