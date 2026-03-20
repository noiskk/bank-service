package com.bank.entity;

import com.bank.common.enums.AccountStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 계좌 엔티티
 * 요구사항: 4.2, 4.3
 */
@Entity
@Table(name = "account_table")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long id;

    /**
     * 계좌 번호 (고유)
     * CARD_DB의 card_account와 연계
     */
    @Column(name = "account_num", unique = true, nullable = false, length = 20)
    private String accountNum;

    /**
     * 현재 통장 잔액
     */
    @Column(name = "amount", nullable = false)
    private Long amount;

    /**
     * 고객 식별 ID
     */
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /**
     * 연동된 카드 번호
     */
    @Column(name = "card_num", nullable = false)
    private String cardNum;

    /**
     * 계좌 상태 (정상, 정지, 해지)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus;

    /**
     * 최소 잔액 (출금 가능 금액 계산 시 사용)
     */
    @Column(name = "minimum_balance", nullable = false)
    private Long minimumBalance;

    /**
     * 개설 일시
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 잔액 차감 (출금)
     */
    public void debit(Long requestAmount) {
        if (requestAmount <= 0) {
            throw new IllegalArgumentException("출금 금액은 0보다 커야 합니다");
        }

        Long availableBalance = this.amount - this.minimumBalance;
        if (requestAmount > availableBalance) {
            throw new IllegalStateException("출금 가능 금액이 부족합니다");
        }

        this.amount -= requestAmount;
    }

    /**
     * 잔액 증가 (입금)
     */
    public void credit(Long requestAmount) {
        if (requestAmount <= 0) {
            throw new IllegalArgumentException("입금 금액은 0보다 커야 합니다");
        }

        this.amount += requestAmount;
    }

    /**
     * 출금 가능 금액 계산
     */
    public Long getAvailableBalance() {
        return this.amount - this.minimumBalance;
    }
}