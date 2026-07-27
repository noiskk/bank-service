package com.bank.service;

import com.bank.entity.Account;
import com.bank.entity.Transaction;
import com.bank.exception.AccountNotActiveException;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.InsufficientBalanceException;
import com.bank.exception.InvalidRequestException;
import com.bank.repository.AccountRepository;
import com.bank.repository.TransactionRepository;
import com.bank.common.enums.AccountStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 계좌 서비스
 * 요구사항: 1. 카드 번호로 계좌 조회 -> 2. 잔액 조회 -> 3. 출금 처리
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    /**
     * [Step 1] 카드 번호로 계좌 조회
     */
    @Transactional(readOnly = true)
    public Account findAccountByCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.trim().isEmpty()) {
            throw new InvalidRequestException("카드 번호는 필수입니다");
        }

        log.debug("카드 번호로 계좌 조회 시작: cardNumber={}", cardNumber);

        return accountRepository.findByCardNum(cardNumber)
                .orElseThrow(() -> new AccountNotFoundException("해당 카드와 연동된 계좌를 찾을 수 없습니다: " + cardNumber));
    }

    /**
     * [Step 2] 계좌를 통한 잔액 조회 및 출금 가능 여부 확인
     */
    @Transactional(readOnly = true)
    public BalanceCheckResult checkBalance(String accountNum, Long requestAmount) {
        if (accountNum == null || accountNum.trim().isEmpty()) {
            throw new InvalidRequestException("계좌 번호는 필수입니다");
        }
        if (requestAmount == null || requestAmount <= 0) {
            throw new InvalidRequestException("요청 금액은 0보다 커야 합니다");
        }

        Account account = accountRepository.findByAccountNum(accountNum)
                .orElseThrow(() -> new AccountNotFoundException("계좌를 찾을 수 없습니다: " + accountNum));

        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(accountNum, account.getAccountStatus().toString());
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

    /** 취소 거래를 원거래와 구분하기 위한 참조번호 접두사 */
    private static final String CANCEL_REF_PREFIX = "CANCEL-";

    /**
     * [Step 3] 출금 처리 (비관적 락 적용)
     *
     * @param referenceId 카드사 거래번호. 거래 내역에 남겨두면 이후 취소·대사에서 원거래를 찾을 수 있다.
     */
    @Transactional
    public DebitResult processDebit(String accountNum, Long amount, String referenceId) {
        if (accountNum == null || accountNum.trim().isEmpty()) {
            throw new InvalidRequestException("계좌 번호는 필수입니다");
        }
        if (amount == null || amount <= 0) {
            throw new InvalidRequestException("출금 금액은 0보다 커야 합니다");
        }

        log.info("출금 처리 시작: accountNum={}, amount={}", accountNum, amount);

        // 비관적 락을 사용하여 계좌 조회 (출금 시점의 동시성 제어)
        Account account = accountRepository.findByAccountNumberWithLock(accountNum)
                .orElseThrow(() -> new AccountNotFoundException("계좌를 찾을 수 없습니다: " + accountNum));

        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(accountNum, account.getAccountStatus().toString());
        }

        // 엔티티 내부 메서드를 통한 잔액 차감
        try{
            account.debit(amount);
        } catch (IllegalStateException e) {
            throw new InsufficientBalanceException(accountNum, amount);
        }

        // 거래 내역 저장
        String transactionId = generateTransactionId();
        Transaction transaction = Transaction.builder()
                .transactionId(transactionId)
                .accountNumber(account.getAccountNum())
                .amount(amount)
                .balanceAfter(account.getAmount())
                .referenceId(referenceId)
                .description("카드 결제 출금")
                .transactionDate(LocalDateTime.now())
                .build();

        transactionRepository.save(transaction);

        log.info("출금 처리 완료: transactionId={}, balanceAfter={}", transactionId, account.getAmount());

        return DebitResult.builder()
                .success(true)
                .transactionId(transactionId)
                .accountNumber(account.getAccountNum())
                .amount(amount)
                .balanceAfter(account.getAmount())
                .transactionDate(transaction.getTransactionDate())
                .build();
    }

    /**
     * 출금 취소(망취소) 처리.
     *
     * 카드사가 승인 후 정합성 문제를 발견했을 때 호출한다. 세 가지 경우를 구분해서 응답한다:
     *  1) 이미 취소된 거래   → 아무것도 하지 않고 성공 (같은 취소 요청이 반복돼도 잔액이 두 번 늘지 않는다)
     *  2) 원거래가 없음      → 애초에 출금이 안 된 것. 취소할 게 없으므로 originalFound=false로 알려준다
     *  3) 원거래 존재        → 잔액을 되돌리고 취소 거래를 기록
     */
    @Transactional
    public CancelResult cancelDebit(String referenceId) {
        if (referenceId == null || referenceId.trim().isEmpty()) {
            throw new InvalidRequestException("원거래 ID는 필수입니다");
        }

        String cancelRef = CANCEL_REF_PREFIX + referenceId;

        // 1) 멱등성: 이미 취소된 거래면 잔액을 다시 건드리지 않는다
        Optional<Transaction> alreadyCancelled = transactionRepository.findByReferenceId(cancelRef);
        if (alreadyCancelled.isPresent()) {
            log.info("이미 취소된 거래 - referenceId={}", referenceId);
            return CancelResult.builder()
                    .success(true)
                    .originalFound(true)
                    .balanceAfter(alreadyCancelled.get().getBalanceAfter())
                    .message("이미 취소된 거래입니다")
                    .build();
        }

        // 2) 원거래가 없으면 출금 자체가 일어나지 않은 것
        Optional<Transaction> original = transactionRepository.findByReferenceId(referenceId);
        if (original.isEmpty()) {
            log.info("원거래 없음 - referenceId={} (출금 미발생)", referenceId);
            return CancelResult.builder()
                    .success(true)
                    .originalFound(false)
                    .message("원거래가 존재하지 않습니다 (출금 미발생)")
                    .build();
        }

        // 3) 실제 취소 — 요청 금액이 아니라 원거래 금액만큼 되돌린다
        Transaction origin = original.get();
        Account account = accountRepository.findByAccountNumberWithLock(origin.getAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException("계좌를 찾을 수 없습니다: " + origin.getAccountNumber()));

        account.credit(origin.getAmount());

        Transaction cancelTx = Transaction.builder()
                .transactionId(generateTransactionId())
                .accountNumber(account.getAccountNum())
                .amount(origin.getAmount())
                .balanceAfter(account.getAmount())
                .referenceId(cancelRef)
                .description("카드 결제 취소(망취소)")
                .transactionDate(LocalDateTime.now())
                .build();
        transactionRepository.save(cancelTx);

        log.info("출금 취소 완료 - referenceId={}, 복구금액={}, 잔액={}",
                referenceId, origin.getAmount(), account.getAmount());

        return CancelResult.builder()
                .success(true)
                .originalFound(true)
                .balanceAfter(account.getAmount())
                .message("취소 완료")
                .build();
    }

    private String generateTransactionId() {
        return "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }

    @lombok.Builder
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class CancelResult {
        private boolean success;
        private boolean originalFound;
        private Long balanceAfter;
        private String message;
    }

    @lombok.Builder
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class BalanceCheckResult {
        private String accountNumber;
        private Long amount;
        private Long minimumBalance;
        private Long availableBalance;
        private Long requestAmount;
        private boolean canWithdraw;
    }

    @lombok.Builder
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class DebitResult {
        private boolean success;
        private String transactionId;
        private String accountNumber;
        private Long amount;
        private Long balanceAfter;
        private LocalDateTime transactionDate;
    }
}