package com.bank.exception;

import org.springframework.http.HttpStatus;

/**
 * 카드번호 또는 계좌번호로 계좌를 찾지 못했음을 나타내는 예외.
 *
 * 찾는 데 실패한 시점이라 accountNumber를 확정할 수 없다 -> null.
 * 정상적인 비즈니스 거절이므로 HTTP 200 (Feign 호출 체인에서 예외로 오인되지 않게).
 */
public class AccountNotFoundException extends BusinessException {
    public AccountNotFoundException(String message) {
        super(message, "14", HttpStatus.OK, null, null);
    }
}