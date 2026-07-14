package com.bank.service;

import com.bank.common.enums.AccountStatus;
import com.bank.entity.Account;
import com.bank.repository.AccountRepository;
import com.bank.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 출금 동시성 테스트 (비관적 락 검증)
 * 가짜(Mock)가 아니라 진짜 앱 + 진짜 DB(H2) + 진짜 스레드로 race condition을 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("출금 동시성 테스트 (비관적 락)")
class ConcurrencyTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private static final String ACCOUNT_NUM = "9999888877776666";

    @BeforeEach
    void setUp() {
        // 매 테스트 전 깨끗이 비우고, 잔액 10만원짜리 계좌 하나 심는다
        transactionRepository.deleteAll();
        accountRepository.deleteAll();

        Account account = Account.builder()
                .accountNum(ACCOUNT_NUM)
                .cardNum("1111222233334444")
                .amount(100_000L)
                .minimumBalance(0L)   // 최소잔액 0 → 10만원 전부 출금 가능
                .customerId(1L)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        accountRepository.save(account);
    }

    @Test
    @DisplayName("10명이 동시에 1만원씩 출금 → 정확히 10건 성공, 잔액 0원")
    void concurrentDebit_exactlyTenSucceed() throws InterruptedException {
        int threadCount = 10;
        long amount = 10_000L;

        DebitOutcome outcome = runConcurrentDebits(threadCount, amount);

        Account after = accountRepository.findByAccountNum(ACCOUNT_NUM).orElseThrow();

        // 락이 제대로 걸렸다면: 10건 전부 성공, 잔액은 정확히 0원 (갱신 유실 없음)
        assertThat(outcome.success.get()).isEqualTo(10);
        assertThat(outcome.fail.get()).isEqualTo(0);
        assertThat(after.getAmount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("15명이 동시에 1만원씩 출금 → 10건 성공, 5건 잔액부족 실패, 잔액 0원")
    void concurrentDebit_overdraftBlocked() throws InterruptedException {
        int threadCount = 15;
        long amount = 10_000L;

        DebitOutcome outcome = runConcurrentDebits(threadCount, amount);

        Account after = accountRepository.findByAccountNum(ACCOUNT_NUM).orElseThrow();

        // 10만원으로는 1만원 출금 10번까지만 가능 → 나머지 5건은 잔액부족으로 막혀야 한다
        assertThat(outcome.success.get()).isEqualTo(10);
        assertThat(outcome.fail.get()).isEqualTo(5);
        assertThat(after.getAmount()).isEqualTo(0L);
    }

    /**
     * threadCount 개의 스레드가 동시에 같은 계좌에서 amount 만큼 출금하도록 실행한다.
     */
    private DebitOutcome runConcurrentDebits(int threadCount, long amount) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger fail = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    accountService.processDebit(ACCOUNT_NUM, amount);
                    success.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS); // 모든 스레드 끝날 때까지 대기
        pool.shutdown();

        return new DebitOutcome(success, fail);
    }

    private record DebitOutcome(AtomicInteger success, AtomicInteger fail) {
    }
}
