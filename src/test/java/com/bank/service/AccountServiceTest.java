package com.bank.service;

import com.bank.common.enums.AccountStatus;
import com.bank.entity.Account;
import com.bank.entity.Transaction;
import com.bank.exception.AccountNotActiveException;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.InsufficientBalanceException;
import com.bank.exception.InvalidRequestException;
import com.bank.repository.AccountRepository;
import com.bank.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AccountService 단위 테스트
 * 진짜 DB 없이 가짜 Repository(Mock)로 서비스 로직만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService 단위 테스트")
class AccountServiceTest {

    // 가짜 창고 직원 2명 (진짜 DB 대신)
    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    // 진짜 시험 대상. 위 가짜 2명이 자동으로 꽂힌다.
    @InjectMocks
    private AccountService accountService;

    private Account testAccount;

    @BeforeEach
    void setUp() {
        // 잔액 10만원, 최소잔액 1만원 → 출금 가능 금액은 9만원
        testAccount = Account.builder()
                .id(1L)
                .accountNum("1234567890")
                .cardNum("1234567812345678")
                .amount(100_000L)
                .minimumBalance(10_000L)
                .customerId(1L)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
    }

    // ---------- findAccountByCardNumber ----------

    @Test
    @DisplayName("카드번호로 계좌 조회 - 성공")
    void findAccountByCardNumber_success() {
        // 대본: 이 카드번호로 물어보면 testAccount를 돌려줘
        when(accountRepository.findByCardNum("1234567812345678"))
                .thenReturn(Optional.of(testAccount));

        Account result = accountService.findAccountByCardNumber("1234567812345678");

        assertThat(result.getAccountNum()).isEqualTo("1234567890");
        // 가짜한테 진짜로 물어봤는지 확인
        verify(accountRepository).findByCardNum("1234567812345678");
    }

    @Test
    @DisplayName("카드번호로 계좌 조회 - 카드번호 null이면 예외")
    void findAccountByCardNumber_null() {
        assertThatThrownBy(() -> accountService.findAccountByCardNumber(null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("카드 번호는 필수입니다");
    }

    @Test
    @DisplayName("카드번호로 계좌 조회 - 연동 계좌 없으면 예외")
    void findAccountByCardNumber_notFound() {
        when(accountRepository.findByCardNum("9999"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.findAccountByCardNumber("9999"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // ---------- checkBalance ----------

    @Test
    @DisplayName("잔액 조회 - 출금 가능 (요청 5만 ≤ 가용 9만)")
    void checkBalance_canWithdraw() {
        when(accountRepository.findByAccountNum("1234567890"))
                .thenReturn(Optional.of(testAccount));

        AccountService.BalanceCheckResult result =
                accountService.checkBalance("1234567890", 50_000L);

        assertThat(result.isCanWithdraw()).isTrue();
        assertThat(result.getAvailableBalance()).isEqualTo(90_000L); // 10만 - 1만
    }

    @Test
    @DisplayName("잔액 조회 - 출금 불가 (요청 9.5만 > 가용 9만)")
    void checkBalance_cannotWithdraw() {
        when(accountRepository.findByAccountNum("1234567890"))
                .thenReturn(Optional.of(testAccount));

        AccountService.BalanceCheckResult result =
                accountService.checkBalance("1234567890", 95_000L);

        assertThat(result.isCanWithdraw()).isFalse();
    }

    @Test
    @DisplayName("잔액 조회 - 정지 계좌면 예외")
    void checkBalance_inactive() {
        Account suspended = Account.builder()
                .accountNum("1234567890")
                .amount(100_000L)
                .minimumBalance(10_000L)
                .accountStatus(AccountStatus.SUSPENDED)
                .build();
        when(accountRepository.findByAccountNum("1234567890"))
                .thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> accountService.checkBalance("1234567890", 10_000L))
                .isInstanceOf(AccountNotActiveException.class)
                .hasMessageContaining("계좌 상태가 정상이 아닙니다");
    }

    // ---------- processDebit ----------

    @Test
    @DisplayName("출금 처리 - 성공 (5만 출금 후 잔액 5만)")
    void processDebit_success() {
        // 출금은 '락 걸고 조회'하는 메서드를 쓴다
        when(accountRepository.findByAccountNumberWithLock("1234567890"))
                .thenReturn(Optional.of(testAccount));
        // 거래내역 저장은 넘어온 객체를 그대로 돌려주도록 흉내
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AccountService.DebitResult result =
                accountService.processDebit("1234567890", 50_000L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBalanceAfter()).isEqualTo(50_000L); // 10만 - 5만
        assertThat(result.getTransactionId()).startsWith("TXN-");
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    @DisplayName("출금 처리 - 잔액 부족이면 예외")
    void processDebit_insufficient() {
        when(accountRepository.findByAccountNumberWithLock("1234567890"))
                .thenReturn(Optional.of(testAccount));

        // 가용 9만원 초과 요청
        assertThatThrownBy(() -> accountService.processDebit("1234567890", 95_000L))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessage("출금 가능 금액이 부족합니다");
    }

    @Test
    @DisplayName("출금 처리 - 정지 계좌면 예외")
    void processDebit_inactive() {
        Account suspended = Account.builder()
                .accountNum("1234567890")
                .amount(100_000L)
                .minimumBalance(10_000L)
                .accountStatus(AccountStatus.SUSPENDED)
                .build();
        when(accountRepository.findByAccountNumberWithLock("1234567890"))
                .thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> accountService.processDebit("1234567890", 10_000L))
                .isInstanceOf(AccountNotActiveException.class)
                .hasMessageContaining("계좌 상태가 정상이 아닙니다");
    }
}
