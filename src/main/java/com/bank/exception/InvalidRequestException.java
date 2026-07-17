package com.bank.exception;

import org.springframework.http.HttpStatus;

/**
 * 요청 자체가 형식적으로 잘못됐음을 나타내는 예외.
 * (예: 카드번호/계좌번호 누락, 금액이 0 이하)
 *
 * 계좌 조회 이전 단계에서 발생하므로 accountNumber를 아직 모른다 -> null.
 */
public class InvalidRequestException extends BusinessException{
    public InvalidRequestException(String message) {
        super(message, "96", HttpStatus.BAD_REQUEST, null, null);
    }
}
