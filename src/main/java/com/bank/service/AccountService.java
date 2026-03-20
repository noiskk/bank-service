package com.bank.service;

import com.bank.entity.Account;
import com.bank.entity.Transaction;
import com.bank.repository.AccountRepository;
import com.bank.repository.TransactionRepository;
import com.bank.common.enums.AccountStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
            throw new IllegalArgumentException("카드 번호는 필수입니다");
        }

        log.debug("카드 번호로 계좌 조회 시작: cardNumber={}", cardNumber);

        return accountRepository.findByCardNum(cardNumber)
                .orElseThrow(() -> new IllegalStateException("해당 카드와 연동된 계좌를 찾을 수 없습니다: " + cardNumber));
    }

    /**
     * [Step 2] 계좌를 통한 잔액 조회 및 출금 가능 여부 확인
     */
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

    /**
     * [Step 3] 출금 처리 (비관적 락 적용)
     */
    @Transactional
    public DebitResult processDebit(String accountNum, Long amount) {
        if (accountNum == null || accountNum.trim().isEmpty()) {
            throw new IllegalArgumentException("계좌 번호는 필수입니다");
        }
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("출금 금액은 0보다 커야 합니다");
        }

        log.info("출금 처리 시작: accountNum={}, amount={}", accountNum, amount);

        // 비관적 락을 사용하여 계좌 조회 (출금 시점의 동시성 제어)
        Account account = accountRepository.findByAccountNumberWithLock(accountNum)
                .orElseThrow(() -> new IllegalStateException("계좌를 찾을 수 없습니다: " + accountNum));

        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("출금 불가 계좌 상태입니다: " + account.getAccountStatus());
        }

        // 엔티티 내부 메서드를 통한 잔액 차감
        account.debit(amount);

        // 거래 내역 저장
        String transactionId = generateTransactionId();
        Transaction transaction = Transaction.builder()
                .transactionId(transactionId)
                .accountNumber(account.getAccountNum())
                .amount(amount)
                .balanceAfter(account.getAmount())
                //.referenceId(account.getCustomerId())
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

    private String generateTransactionId() {
        return "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
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