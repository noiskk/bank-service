package com.bank.exception;

import org.springframework.http.HttpStatus;

/**
 * 계좌 상태가 ACTIVE가 아님을 나타내는 예외 (정지/해지 등).
 */
public class AccountNotActiveException extends BusinessException {
    public AccountNotActiveException(String accountNumber, String status) {
        super("계좌 상태가 정상이 아닙니다: " + status, "14", HttpStatus.OK, accountNumber, null);
    }
}