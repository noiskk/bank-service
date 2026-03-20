package com.bank.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 거래 내역 엔티티
 * 계좌의 모든 거래 내역을 기록
 * 요구사항: 4.3, 4.8, 11.9
 */
@Entity
@Table(name = "transactions")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Transaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 거래 고유 번호
     */
    @Column(unique = true, nullable = false, length = 50)
    private String transactionId;
    
    /**
     * 계좌 번호
     */
    @Column(nullable = false, length = 20)
    private String accountNumber;
    
    /**
     * 거래 금액
     */
    @Column(nullable = false, precision = 15, scale = 2)
    private Long amount;
    
    /**
     * 거래 후 잔액
     */
    @Column(nullable = false, precision = 15, scale = 2)
    private Long balanceAfter;

    /**
     * 참조 ID (승인 번호, 이체 ID 등)
     */
    @Column(length = 50)
    private String referenceId;
    
    /**
     * 거래 설명
     */
    @Column(length = 200)
    private String description;
    
    /**
     * 거래 일시
     */
    @Column(nullable = false)
    private LocalDateTime transactionDate;
}
