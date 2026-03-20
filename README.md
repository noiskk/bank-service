# 🏦 Bank Service (계좌 및 출금 관리)

## 📖 개요

고객의 계좌 잔액을 조회하고 카드사의 승인 요청에 따라 실제 출금 처리를 수행

<img width="866" height="490" alt="bank-service" src="https://github.com/user-attachments/assets/e1eadb80-54c5-42f2-8060-7993233afb1b" />


## 🌐 API Endpoints

| Method | URI | Description |
| --- | --- | --- |
| `POST` | `/api/bank/accounts/withdraw` | 카드 번호 기반 연동 계좌 출금 처리 |

## ✨ 주요 기능

**1. 3단계 안전 출금 프로세스**

- **Step 1:** 결제 요청으로 들어온 카드 번호(`cardNum`)를 기반으로 연동된 계좌 번호 조회
- **Step 2:** 해당 계좌의 현재 잔액을 조회하여 출금 가능 여부(잔액 부족 등) 사전 검증
- **Step 3:** 검증 통과 시 실제 계좌 잔액 차감 및 트랜잭션 기록 완료

**2. 동시성 제어 (Concurrency Control)**

- 동일 계좌에 다수의 결제/출금 요청이 동시에 발생할 경우를 대비하여 데이터 무결성 보장
- JPA `@Lock(LockModeType.PESSIMISTIC_WRITE)`을 활용한 비관적 락(Pessimistic Lock) 적용
    - 이미 트랜잭션이 생성되어 있는 경우 앞의 요청의 처리가 완료된 이후 트랜잭션 요청 처리

**3. 금융권 표준 에러 코드 응답**

- `00`: 정상 승인
- `51`: 잔액 부족
- `96`: 시스템 오류 및 필수 파라미터 누락

## ⚙️ DB Schema

### account_table

[Account.java](https://github.com/fisa-msa-project/bank-service/blob/main/src/main/java/com/bank/entity/Account.java#L18C1-L110C2)

| **필드명 (Field)** | **데이터 타입 (Type)** | **DB 컬럼명 (Column)** | **제약 조건 (Constraints)** | **설명 (Description)** |
| --- | --- | --- | --- | --- |
| **`id`** | `Long` | `account_id` | `PK`, `Auto Increment` | 계좌 테이블 기본키 (고유 식별자) |
| **`accountNum`** | `String` | `account_num` | `NOT NULL`, `UNIQUE`, `Length=20` | 계좌 번호. CARD_DB의 card_account와 연계 |
| **`amount`** | `Long` | `amount` | `NOT NULL` | 현재 통장 잔액 |
| **`customerId`** | `Long` | `customer_id` | `NOT NULL` | 고객 식별 ID |
| **`cardNum`** | `String` | `card_num` | `NOT NULL` | 연동된 카드 번호 |
| **`accountStatus`** | `AccountStatus` (Enum) | `account_status` | `NOT NULL`, `Length=20` | 계좌 상태 (정상, 정지, 해지). DB에는 문자열(String)로 저장됨 |
| **`minimumBalance`** | `Long` | `minimum_balance` | `NOT NULL` | 최소 잔액 (출금 가능 금액 계산 시 사용) |
| **`createdAt`** | `LocalDateTime` | `created_at` | `NOT NULL`, `Updatable=false` | 계좌 개설 일시 (Auditing 자동 생성) |

### transaction

[Transaction.java](https://github.com/fisa-msa-project/bank-service/blob/main/src/main/java/com/bank/entity/Account.java#L18C1-L110C2)

| **필드명 (Field)** | **데이터 타입 (Type)** | **DB 컬럼명 (Column)** | **제약 조건 (Constraints)** | **설명 (Description)** |
| --- | --- | --- | --- | --- |
| **`id`** | `Long` | `id` | `PK`, `Auto Increment` | 거래 내역 테이블 기본키 (고유 식별자) |
| **`transactionId`** | `String` | `transaction_id` | `NOT NULL`, `UNIQUE`, `Length=50` | 거래 고유 번호 (UUID 등) |
| **`accountNumber`** | `String` | `account_number` | `NOT NULL`, `Length=20` | 거래가 발생한 계좌 번호 |
| **`amount`** | `Long` | `amount` | `NOT NULL`, `Precision=15`, `Scale=2` | 거래 금액 |
| **`balanceAfter`** | `Long` | `balance_after` | `NOT NULL`, `Precision=15`, `Scale=2` | 거래 후 남은 잔액 |
| **`referenceId`** | `String` | `reference_id` | `Length=50` (Nullable) | 참조 ID (승인 번호, 이체 ID 등) |
| **`description`** | `String` | `description` | `Length=200` (Nullable) | 거래에 대한 설명 (예: "카드 결제", "ATM 출금" 등) |
| **`transactionDate`** | `LocalDateTime` | `transaction_date` | `NOT NULL` | 거래 발생 일시 |

## 💻주요로직

### 카드 번호를 통한 계좌번호 조회

[AccountService.java](https://github.com/fisa-msa-project/bank-service/blob/main/src/main/java/com/bank/service/AccountService.java#L31C5-L41C6)

```java
@Transactional(readOnly = true)
    public Account findAccountByCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("카드 번호는 필수입니다");
        }

        log.debug("카드 번호로 계좌 조회 시작: cardNumber={}", cardNumber);

        return accountRepository.findByCardNum(cardNumber)
                .orElseThrow(() -> new IllegalStateException("해당 카드와 연동된 계좌를 찾을 수 없습니다: " + cardNumber));
    }
```

### 계좌 조회를 통해 출금 가능 여부 확인 및 출금 처리

[AccountService.java](https://github.com/fisa-msa-project/bank-service/blob/main/src/main/java/com/bank/service/AccountService.java#L46-L73)

```java
@Transactional(readOnly = true)
    public BalanceCheckResult checkBalance(String accountNum, Long requestAmount) {
        if (accountNum == null || accountNum.trim().isEmpty()) {
            throw new IllegalArgumentException("계좌 번호는 필수입니다");
        }
        if (requestAmount == null || requestAmount <= 0) {
            throw new IllegalArgumentException("요청 금액은 0보다 커야 합니다");
        }

        Account account = accountRepository.findByAccountNum(accountNum)
                .orElseThrow(() -> new IllegalStateException("계좌를 찾을 수 없습니다: " + accountNum));

        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("계좌 상태가 정상이 아닙니다: " + account.getAccountStatus());
        }

        Long availableBalance = account.getAvailableBalance();
        boolean canWithdraw = requestAmount <= availableBalance;

        return BalanceCheckResult.builder()
                .accountNumber(accountNum)
                .amount(account.getAmount())
                .minimumBalance(account.getMinimumBalance())
                .availableBalance(availableBalance)
                .requestAmount(requestAmount)
                .canWithdraw(canWithdraw)
                .build();
    }
```
