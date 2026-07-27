package com.bank.service;

import com.bank.common.enums.AccountStatus;
import com.bank.entity.Account;
import com.bank.entity.Transaction;
import com.bank.repository.AccountRepository;
import com.bank.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 출금 취소(망취소) 테스트.
 * 취소는 카드사가 재시도할 수 있으므로 "여러 번 호출해도 잔액이 한 번만 복구되는지"가 핵심이다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("출금 취소(망취소) 테스트")
class AccountCancelTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private AccountService accountService;

    private static final String REF = "CARD-TX-1";
    private Account account;

    @BeforeEach
    void setUp() {
        account = Account.builder()
                .accountNum("1234567890")
                .amount(50_000L)
                .minimumBalance(10_000L)
                .accountStatus(AccountStatus.ACTIVE)
                .cardNum("4111111111111111")
                .customerId(1L)
                .build();
    }

    @Test
    @DisplayName("원거래가 없으면 잔액을 건드리지 않고 originalFound=false로 알린다")
    void cancel_originalNotFound() {
        when(transactionRepository.findByReferenceId("CANCEL-" + REF)).thenReturn(Optional.empty());
        when(transactionRepository.findByReferenceId(REF)).thenReturn(Optional.empty());

        AccountService.CancelResult result = accountService.cancelDebit(REF);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isOriginalFound()).isFalse();
        verify(accountRepository, never()).findByAccountNumberWithLock(anyString());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("원거래가 있으면 원거래 금액만큼 잔액을 복구하고 취소 거래를 남긴다")
    void cancel_restoresBalance() {
        when(transactionRepository.findByReferenceId("CANCEL-" + REF)).thenReturn(Optional.empty());
        when(transactionRepository.findByReferenceId(REF)).thenReturn(Optional.of(originalWithdrawal(30_000L)));
        when(accountRepository.findByAccountNumberWithLock("1234567890")).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountService.CancelResult result = accountService.cancelDebit(REF);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isOriginalFound()).isTrue();
        assertThat(account.getAmount()).isEqualTo(80_000L); // 5만 + 복구 3만
        assertThat(result.getBalanceAfter()).isEqualTo(80_000L);

        verify(transactionRepository).save(argThat(tx ->
                ("CANCEL-" + REF).equals(tx.getReferenceId()) && tx.getAmount() == 30_000L));
    }

    @Test
    @DisplayName("이미 취소된 거래를 다시 취소해도 잔액이 두 번 늘지 않는다 (멱등)")
    void cancel_idempotent() {
        Transaction alreadyCancelled = Transaction.builder()
                .transactionId("TXN-CANCEL")
                .accountNumber("1234567890")
                .amount(30_000L)
                .balanceAfter(80_000L)
                .referenceId("CANCEL-" + REF)
                .description("카드 결제 취소(망취소)")
                .transactionDate(LocalDateTime.now())
                .build();
        when(transactionRepository.findByReferenceId("CANCEL-" + REF)).thenReturn(Optional.of(alreadyCancelled));

        AccountService.CancelResult result = accountService.cancelDebit(REF);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isOriginalFound()).isTrue();
        assertThat(result.getBalanceAfter()).isEqualTo(80_000L);

        // 잔액 재복구도, 추가 거래 기록도 없어야 한다
        verify(accountRepository, never()).findByAccountNumberWithLock(anyString());
        verify(transactionRepository, never()).save(any(Transaction.class));
        assertThat(account.getAmount()).isEqualTo(50_000L);
    }

    private Transaction originalWithdrawal(long amount) {
        return Transaction.builder()
                .transactionId("TXN-ORIGIN")
                .accountNumber("1234567890")
                .amount(amount)
                .balanceAfter(50_000L)
                .referenceId(REF)
                .description("카드 결제 출금")
                .transactionDate(LocalDateTime.now())
                .build();
    }
}
