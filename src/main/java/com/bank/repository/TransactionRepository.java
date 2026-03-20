package com.bank.repository;

import com.bank.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 거래 내역 리포지토리
 * 요구사항: 4.3, 4.8
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    
    /**
     * 거래 고유 번호로 거래 조회
     */
    Optional<Transaction> findByTransactionId(String transactionId);
    
    /**
     * 계좌 번호로 거래 내역 조회
     */
    List<Transaction> findByAccountNumberOrderByTransactionDateDesc(String accountNumber);
    
    /**
     * 참조 ID로 거래 조회
     */
    Optional<Transaction> findByReferenceId(String referenceId);
}
