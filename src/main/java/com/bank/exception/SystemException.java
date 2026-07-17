package com.bank.exception;

import org.springframework.http.HttpStatus;

/**
 * 예상하지 못한 실패를 나타내는 예외.
 *
 * bank-service는 지금 다른 서비스를 호출하지 않아 구체적인 시스템 예외가 아직 없다.
 * 4단계(망취소)에서 원천 확인/재조회 로직이 추가되면 그때 구체 클래스가 생긴다.
 * 지금은 계층 구조만 미리 갖춰두고, GlobalExceptionHandler의 안전망(catch-all)이 대신한다.
 */
public abstract class SystemException extends DomainException {
    protected SystemException(String message, String errorCode, HttpStatus httpStatus,
                              String accountNumber, Long amount) {
        super(message, errorCode, httpStatus, accountNumber, amount);
    }
}