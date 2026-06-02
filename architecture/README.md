# Pingdom Backend 아키텍처

## 1. 문서 목적

이 문서는 Pingdom Backend의 목표 아키텍처를 정의한다.

이 문서는 다음 목적을 가진다.

- 구조 개편 시 공통 기준으로 사용한다.
- 모듈 책임과 의존 방향을 고정한다.
- CQRS 적용 범위를 명확히 구분한다.
- 이벤트 기반 처리 범위를 명확히 구분한다.
- 유지보수성, 확장성, 디버깅 가능성을 우선 기준으로 삼는다.

## 2. 채택 아키텍처

Pingdom Backend는 이벤트 기반 모듈러 모놀리스를 기본 구조로 채택한다.

조회 복잡도가 높은 일부 영역에는 선택적으로 CQRS를 적용한다.

이 구조는 다음 전제를 따른다.

- 배포 단위는 하나의 백엔드 애플리케이션으로 유지한다.
- 도메인 책임은 모듈 단위로 분리한다.
- 모듈 간 후속 처리와 부수효과는 이벤트로 분리한다.
- 모든 기능에 CQRS를 적용하지 않는다.
- 강한 정합성이 필요한 핵심 상태 변경은 동기 트랜잭션으로 처리한다.

## 3. 이 구조를 채택하는 이유

### 3-1. 유지보수성

- 인증, 지도 장소, 게시글, 좋아요, 신고, 관리자 제재, 알림이 하나의 서비스 계층에 계속 누적되면 책임이 빠르게 섞인다.
- 모듈 경계를 먼저 고정하면 수정 범위를 예측하기 쉬워진다.
- 읽기 모델과 쓰기 모델이 성격이 다른 영역만 CQRS로 분리하면 불필요한 복잡도를 줄일 수 있다.

### 3-2. 확장성

- 알림 채널 추가, OAuth Provider 추가, 조회 화면 증가, 관리자 기능 확장은 현재 구조보다 더 자주 발생할 가능성이 높다.
- 이벤트 기반 구조는 후속 처리와 외부 연동을 점진적으로 추가하기에 유리하다.
- 모놀리스를 유지하므로 초기 운영 복잡도를 통제하면서도 장기 확장 여지를 확보할 수 있다.

### 3-3. 디버깅

- 마이크로서비스보다 요청 흐름 추적이 단순하다.
- 동시에 단순 계층형 구조보다 이벤트 단위의 상태 추적이 가능하다.
- 어떤 명령이 어떤 후속 처리를 발생시켰는지 로그와 이벤트 기준으로 재구성할 수 있다.

## 4. 핵심 원칙

### 4-1. 모듈 우선 원칙

- 기능은 기술 계층보다 도메인 모듈 기준으로 먼저 나눈다.
- 하나의 모듈은 하나의 핵심 책임을 가져야 한다.
- 다른 모듈의 내부 구현에 직접 의존하지 않는다.

### 4-2. 동기 처리와 비동기 처리 분리 원칙

- 반드시 함께 성공하거나 함께 실패해야 하는 로직은 동기 트랜잭션으로 묶어야 한다.
- 본 처리 이후에 이어지는 부수효과는 이벤트로 분리해야 한다.
- 알림, 메일 발송, Projection 갱신, 후속 집계는 이벤트 우선으로 처리한다.

### 4-3. 선택적 CQRS 원칙

- 조회 화면이 단순하면 CQRS를 적용하지 않는다.
- 조회 요구사항이 복잡할 때만 Query 모델을 별도로 둔다.
- Command 모델은 상태 변경과 규칙 검증에 집중한다.
- Query 모델은 정렬, 조합, 집계, 응답 최적화에 집중한다.

### 4-4. 외부 연동 격리 원칙

- S3, Firebase, OAuth, Email, JWT 같은 외부 의존성은 모듈 내부 핵심 규칙과 분리해야 한다.
- 외부 SDK 호출 코드는 도메인 규칙을 직접 담지 않는다.
- 외부 연동 실패는 모듈 경계에서 번역된 예외와 이벤트 처리 정책으로 다뤄야 한다.

### 4-5. 디버깅 우선 원칙

- 이벤트에는 추적 가능한 식별자를 남겨야 한다.
- 실패 지점은 명령 처리 실패인지, 이벤트 처리 실패인지 구분 가능해야 한다.
- 로그는 사용자 요청, 명령, 이벤트, 외부 연동 실패를 분리해서 남겨야 한다.

## 5. 모듈 구성

현재 Pingdom Backend는 다음 모듈 구성을 목표로 한다.

| 모듈 | 책임 |
|---|---|
| identity | 회원가입, 로그인, OAuth, 토큰, 내 계정 상태 |
| place | 장소 생성, 좌표 처리, 장소 북마크 |
| post | 지도 게시글 업로드, 게시글 삭제, 게시글 메타데이터 |
| engagement | 좋아요, 신고, 사용자 상호작용 |
| moderation | 관리자 조회, 신고 처리, 게시글 제재, 사용자 제재 |
| notification | 이메일, FCM, 후속 알림 처리 |
| shared | 보안, 공통 예외, 공통 설정, 공통 기술 지원 |

각 모듈은 다음 규칙을 따라야 한다.

- 모듈은 자기 책임 범위의 Use Case를 소유한다.
- 모듈은 자기 데이터 변경 책임을 우선 소유한다.
- 다른 모듈의 Repository를 직접 호출하지 않는 방향을 목표로 한다.
- 다른 모듈과 협력이 필요하면 우선 공개 Use Case 또는 이벤트를 통해 협력한다.

## 6. 모듈 내부 구조

모듈 내부는 다음 구조를 기본으로 한다.

```text
<module>
 ├─ api
 ├─ application
 ├─ domain
 ├─ infrastructure
 └─ event
```

각 계층의 책임은 다음과 같다.

| 계층 | 책임 |
|---|---|
| api | Controller, Request, Response, 인증 컨텍스트 매핑 |
| application | Use Case, Command Handler, Query Handler, 트랜잭션 경계 |
| domain | Entity, Domain Service, Domain Rule, Value Object |
| infrastructure | Repository 구현, 외부 SDK 연동, 메시징, 저장소 연동 |
| event | 도메인 이벤트 정의, 이벤트 발행, 이벤트 소비 |

모듈 내부 규칙은 다음과 같다.

- api는 application만 호출해야 한다.
- application은 domain을 사용해 흐름을 조립해야 한다.
- infrastructure는 domain 규칙의 소유자가 되어서는 안 된다.
- event consumer는 부수효과 처리와 후속 갱신에 집중해야 한다.

## 7. CQRS 적용 범위

### 7-1. CQRS를 적용해야 하는 영역

- 관리자 게시글 목록 조회
- 관리자 신고 목록 및 신고 상태 조회
- 장소 상세 화면의 게시글/좋아요/신고 조합 조회
- 인기순, 최신순, 오래된순 같은 다중 정렬 조회
- 피드, 검색, 집계성 조회

이 영역은 다음 특성을 가진다.

- 쓰기 모델과 응답 모델이 다르다.
- 여러 엔티티 조합과 정렬이 필요하다.
- 화면 요구사항 때문에 응답 형태가 자주 변한다.

### 7-2. CQRS를 적용하지 않는 영역

- 로그인
- 회원가입
- 토큰 재발급
- 비밀번호 변경
- 아이디 변경
- 단순 장소 생성
- 단순 북마크 생성

이 영역은 다음 특성을 가진다.

- 상태 변경 규칙이 핵심이다.
- 읽기 모델을 따로 분리할 필요가 작다.
- 단일 트랜잭션과 검증 흐름이 더 중요하다.

## 8. 이벤트 적용 범위

이벤트는 후속 처리와 부수효과를 분리하기 위해 사용한다.

우선 적용 대상은 다음과 같다.

- 회원가입 후 이메일 인증 메일 발송
- 좋아요 후 FCM 알림 발송
- 신고 수락 후 후속 알림 또는 집계 갱신
- 관리자 처리 이후 Projection 갱신
- 게시글 변경 후 피드/목록 조회 모델 갱신

이벤트는 다음 규칙을 따라야 한다.

- 이벤트 이름은 과거 시제로 정의한다.
- 이벤트는 이미 확정된 사실만 표현해야 한다.
- 이벤트 소비자는 동일 이벤트를 중복 처리해도 안전해야 한다.
- 이벤트 실패는 원인 로그와 재처리 가능성을 함께 고려해야 한다.

예시 이벤트는 다음과 같다.

- `UserSignedUp`
- `EmailVerificationRequested`
- `PostCreated`
- `PostDeleted`
- `ImageLiked`
- `PostReported`
- `ReportAccepted`
- `UserBanned`

## 9. 트랜잭션과 정합성 원칙

### 9-1. 동기 트랜잭션으로 처리해야 하는 영역

- 인증 정보 저장과 토큰 상태 반영
- 신고 수락 시 신고 상태 변경
- 신고 수락 시 사용자 제재 상태 반영
- 게시글 생성과 핵심 DB 저장
- 게시글 삭제와 핵심 DB 삭제

### 9-2. 이벤트로 분리해야 하는 영역

- 메일 발송
- FCM 알림 발송
- 조회 모델 재구성
- 후속 집계 갱신

### 9-3. 정합성 처리 원칙

- 핵심 도메인 상태는 먼저 로컬 트랜잭션으로 확정해야 한다.
- 외부 시스템 후속 처리는 이벤트 기반으로 분리해야 한다.
- 외부 자원과 DB가 함께 얽히는 흐름은 롤백 보정 전략을 가져야 한다.
- 이벤트 발행은 트랜잭션 성공 이후 기준으로 관리해야 한다.

## 10. 패키지 구조 예시

다음은 목표 패키지 구조 예시다.

```text
src/main/java/com/typenull/pingdom
├─ shared
│  ├─ config
│  ├─ security
│  ├─ exception
│  └─ support
├─ identity
│  ├─ api
│  │  ├─ AuthController.java
│  │  └─ UserController.java
│  ├─ application
│  │  ├─ command
│  │  │  ├─ SignupCommand.java
│  │  │  └─ RefreshTokenCommand.java
│  │  ├─ service
│  │  │  └─ AuthCommandService.java
│  │  └─ query
│  │     └─ MyAccountQueryService.java
│  ├─ domain
│  │  ├─ User.java
│  │  ├─ OAuthAccount.java
│  │  └─ UserRepository.java
│  ├─ infrastructure
│  │  ├─ jwt
│  │  ├─ oauth
│  │  ├─ persistence
│  │  └─ email
│  └─ event
│     ├─ EmailVerificationRequested.java
│     └─ UserSignedUp.java
├─ place
│  ├─ api
│  ├─ application
│  ├─ domain
│  ├─ infrastructure
│  └─ event
├─ post
│  ├─ api
│  ├─ application
│  ├─ domain
│  ├─ infrastructure
│  └─ event
├─ engagement
│  ├─ api
│  ├─ application
│  ├─ domain
│  ├─ infrastructure
│  └─ event
├─ moderation
│  ├─ api
│  ├─ application
│  │  ├─ command
│  │  ├─ query
│  │  └─ service
│  ├─ domain
│  ├─ infrastructure
│  └─ event
└─ notification
   ├─ application
   ├─ domain
   ├─ infrastructure
   └─ event
```

## 11. 의존 규칙

다음 규칙은 반드시 지켜야 한다.

- Controller는 Repository를 직접 호출하지 않는다.
- Query 로직은 Command 로직의 내부 구현을 직접 재사용하지 않는다.
- 모듈 외부에서 다른 모듈의 Entity를 수정하지 않는다.
- 공통 패키지에는 비즈니스 로직을 넣지 않는다.
- 예외는 모듈 책임 기준으로 정의한다.
- 외부 연동 코드는 infrastructure 계층 밖으로 새지 않게 유지한다.

## 12. 구현 우선순위

구조 개편은 다음 순서로 진행한다.

1. 모듈 경계 확정
2. 공통 패키지 축소
3. Command 흐름 정리
4. Query 분리 대상 선정
5. 이벤트 발행 지점 정리
6. Projection 또는 조회 전용 모델 도입
7. 로깅, 추적 ID, 재처리 정책 보강
