# 🏦 Bank Service (계좌 · 출금 · 취소)

> 전체 시스템 개요·아키텍처·실행 방법 → **[card-payment-system](https://github.com/noiskk/card-payment-system)**
> 관련 저장소: [pos-client](https://github.com/noiskk/pos-client) · [van-service](https://github.com/noiskk/van-service) · [card-service](https://github.com/noiskk/card-service)

## 📖 개요

고객 계좌를 관리하고, 카드사의 승인 요청에 따라 실제 출금을 수행한다. 카드사가 정합성 문제를 발견했을 때 출금을 되돌리는 **취소(망취소)** 도 제공한다.

**카드사 외부의 독립 기관**이다. 카드사 내부 서비스 레지스트리에 등록하지 않고 고정 엔드포인트로 연동된다.

## 🌐 API Endpoints

| Method | URI | Description |
|---|---|---|
| `POST` | `/api/bank/accounts/withdraw` | 카드 번호 기반 연동 계좌 출금 |
| `POST` | `/api/bank/accounts/cancel` | 원거래 ID로 출금 취소 (멱등) |
| `GET` | `/` | 계좌 관제 화면 |

---

## ✨ 주요 기능

### 1. 3단계 안전 출금

1. 결제 요청의 카드번호(`cardNum`)로 연동 계좌 조회
2. 잔액 조회 및 출금 가능 여부 사전 검증
3. 검증 통과 시 잔액 차감 + 거래 내역 기록

### 2. 동시성 제어 (비관적 락)

동일 계좌에 출금 요청이 동시에 들어와도 잔액이 어긋나지 않도록 `@Lock(LockModeType.PESSIMISTIC_WRITE)`를 적용했다. 앞선 트랜잭션이 끝난 뒤 다음 요청이 처리된다.

스레드 10개가 동시에 출금을 요청하는 통합 테스트로 검증한다 — 락이 없으면 여러 스레드가 같은 잔액을 읽어 **갱신 유실**이 발생하지만, 락이 있으면 정확히 10건만 성공하고 잔액이 0원이 된다.

### 3. 출금 취소 (망취소) — 멱등

카드사가 승인 후 정합성 문제를 발견했을 때 호출한다. **세 가지 경우를 구분해 응답한다.**

| 상황 | 응답 | 카드사가 아는 것 |
|---|---|---|
| 이미 취소된 거래 | `success=true`, `originalFound=true` | 중복 요청, 잔액은 안 건드림 |
| 원거래 없음 | `success=true`, **`originalFound=false`** | **애초에 출금이 안 됐다** → 종결 |
| 원거래 존재 | `success=true`, `originalFound=true` | 취소 완료 |

`originalFound`가 핵심이다. 카드사의 대사 배치는 "이 거래, 실제로 출금됐나?"를 알아야 하는데, 조회 API를 따로 만들지 않고 **취소 API 한 번의 호출로 확인과 조치를 동시에** 할 수 있게 했다.

**멱등성** — 취소는 카드사 대사 배치가 재시도할 수 있다. 같은 원거래를 여러 번 취소해도 잔액이 두 번 복구되면 안 된다.

```java
String cancelRef = CANCEL_REF_PREFIX + referenceId;

// 1) 멱등성: 이미 취소된 거래면 잔액을 다시 건드리지 않는다
Optional<Transaction> alreadyCancelled = transactionRepository.findByReferenceId(cancelRef);
if (alreadyCancelled.isPresent()) { ... return; }

// 2) 원거래가 없으면 출금 자체가 일어나지 않은 것
Optional<Transaction> original = transactionRepository.findByReferenceId(referenceId);
if (original.isEmpty()) { ... originalFound=false ... }

// 3) 실제 취소 — 요청 금액이 아니라 원거래 금액만큼 되돌린다
account.credit(origin.getAmount());
```

취소 금액은 **요청값이 아니라 원거래에 기록된 금액**을 쓴다. 호출자가 잘못된 금액을 보내도 원거래와 다른 금액이 복구되지 않는다.

### 4. 비즈니스 거절은 HTTP 200으로 응답한다

잔액부족·계좌없음 같은 거절을 4xx로 응답하면 안 된다. 이 API를 호출하는 건 카드사의 OpenFeign 클라이언트인데, **Feign은 비2xx를 받으면 정상 객체가 아니라 예외(`FeignException`)를 던진다.**

그러면 잔액부족처럼 매일 일어나는 정상 거절이 카드사 입장에서 **"은행 호출 실패, 출금 여부 불확실"**로 오분류된다. 카드사는 이를 보상·대사 대상으로 처리하려 하게 된다.

→ 카드사·은행 내부 연동(ISO 8583) 관례를 따라 **"전송은 성공, 결과는 응답코드로"** 통일했다. 형식 자체가 잘못된 요청(`InvalidRequestException`)만 400을 유지해 진짜 malformed request와 정상적인 비즈니스 거절을 구분한다.

거절 사유는 타입 있는 예외(`InsufficientBalanceException` 등)로 던지고 전역 핸들러가 응답을 조립한다. 예외 메시지 문자열을 검사해 분기하면 문구가 바뀔 때 로직이 깨진다.

### 5. 원거래 추적

은행은 자기 거래번호(`TXN-…`)를 발급하고 카드사는 자기 거래번호(UUID)를 갖는다. 둘을 잇는 연결고리가 없으면 "이 승인에 해당하는 출금"을 찾을 수 없다.

→ 출금 요청에 카드사 거래번호를 실어 받아 `referenceId`로 저장한다. 실제 카드 취소 전문(ISO 8583 0400)이 원거래의 STAN/RRN을 실어 보내는 것과 같은 구조다.

취소 거래는 `CANCEL-{원거래ID}` 접두사로 구분해 저장하므로, 원거래와 취소 거래를 짝지어 조회할 수 있다.

---

## 🔢 응답 코드

| 코드 | 의미 | HTTP |
|---|---|---|
| `00` | 정상 처리 | 200 |
| `51` | 잔액 부족 | 200 |
| `14` | 계좌 없음 / 정지 계좌 | 200 |
| `96` | 시스템 오류 | 500 |
| - | 요청 형식 오류 | 400 |

---

## ⚙️ DB Schema

### account_table

| 필드 | 타입 | 컬럼 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `id` | `Long` | `account_id` | PK, Auto Increment | 계좌 기본키 |
| `accountNum` | `String` | `account_num` | NOT NULL, UNIQUE, Length=20 | 계좌 번호 |
| `amount` | `Long` | `amount` | NOT NULL | 현재 잔액 |
| `customerId` | `Long` | `customer_id` | NOT NULL | 고객 식별 ID |
| `cardNum` | `String` | `card_num` | NOT NULL | 연동된 카드 번호 |
| `accountStatus` | `AccountStatus` | `account_status` | NOT NULL, Length=20 | 계좌 상태 (ACTIVE·SUSPENDED·CLOSED) |
| `minimumBalance` | `Long` | `minimum_balance` | NOT NULL | 최소 잔액 (출금 가능액 계산용) |
| `createdAt` | `LocalDateTime` | `created_at` | NOT NULL, Updatable=false | 개설 일시 |

### transactions

| 필드 | 타입 | 컬럼 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `id` | `Long` | `id` | PK, Auto Increment | 거래 내역 기본키 |
| `transactionId` | `String` | `transaction_id` | NOT NULL, UNIQUE, Length=50 | 은행 거래 번호 (`TXN-…`) |
| `accountNumber` | `String` | `account_number` | NOT NULL, Length=20 | 거래 계좌 번호 |
| `amount` | `Long` | `amount` | NOT NULL | 거래 금액 |
| `balanceAfter` | `Long` | `balance_after` | NOT NULL | 거래 후 잔액 |
| `referenceId` | `String` | `reference_id` | Length=50, Nullable | **카드사 거래번호. 취소 거래는 `CANCEL-` 접두사** |
| `description` | `String` | `description` | Length=200, Nullable | 거래 설명 |
| `transactionDate` | `LocalDateTime` | `transaction_date` | NOT NULL | 거래 일시 |

---

## ▶️ 실행

MySQL 없이 H2로 바로 띄울 수 있다.

```bash
sh gradlew bootRun --args='--spring.profiles.active=local'
```

MySQL로 실행하려면 `--args` 없이:

```bash
export DB_PASSWORD=<비밀번호>
sh gradlew bootRun
```

**계좌 관제 화면**: http://localhost:8080 — 계좌 잔액, 거래 내역(출금/취소 구분), 원거래 참조

결제하면 잔액이 줄고, 카드사가 취소를 보내면 복구되는 과정을 화면으로 확인할 수 있다.

### 테스트

```bash
sh gradlew test
```

- 출금 로직 단위 테스트 (Mockito)
- 동시성 검증 통합 테스트 (`@SpringBootTest` + H2 + 실제 스레드)
- 취소 멱등성·잔액 복구 테스트
