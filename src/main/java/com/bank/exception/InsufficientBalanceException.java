package com.bank.exception;

import org.springframework.http.HttpStatus;

/**
 * 출금 가능 금액이 요청 금액보다 부족함을 나타내는 예외.
 */
public class InsufficientBalanceException extends BusinessException {
    public InsufficientBalanceException(String accountNumber, Long amount) {
        super("출금 가능 금액이 부족합니다", "51", HttpStatus.OK, accountNumber, amount);
    }
}