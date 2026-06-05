# Pingdom Backend Docs

이 디렉터리는 Pingdom Backend의 설계 문서를 주제별로 관리한다.

## 디렉터리 구성

- [`architecture`](./architecture/README.md)
  시스템 구조, 모듈 책임, 의존 규칙 같은 아키텍처 문서를 관리한다.
- [`algorithm`](./algorithm/README.md)
  추천, 랭킹, 탐색 정책처럼 알고리즘 설계 문서를 관리한다.

## 관리 원칙

- 실행 코드와 직접 결합되지 않는 설계 문서는 `docs` 아래에서 관리한다.
- 문서는 주제별 디렉터리로 분리하고, 하나의 문서는 하나의 논리 목적만 가진다.
- 구현 전에 참조해야 하는 운영 규칙과 확장 설계는 문서로 먼저 고정한다.
