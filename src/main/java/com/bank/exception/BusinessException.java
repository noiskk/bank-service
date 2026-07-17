package com.bank.exception;

import org.springframework.http.HttpStatus;

/**
 * 계좌/출금 요청이 비즈니스 규칙에 의해 거절되었음을 나타내는 예외.
 * (예: 계좌 없음, 계좌 비활성, 잔액 부족, 잘못된 요청값)
 *
 * 같은 요청을 다시 보내도 동일하게 거절된다 — 부작용이 발생하기 전에 판단되므로 보상 트랜잭션이 필요 없다.
 */
public abstract class BusinessException extends DomainException {
    protected BusinessException(String message, String errorCode, HttpStatus httpStatus, String accountNumber, Long amount) {
        super(message, errorCode, httpStatus, accountNumber, amount);
    }
}
