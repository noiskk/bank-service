package com.bank.exception;

import com.bank.dto.DebitResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<EntityModel<DebitResponse>> handleBusiness(BusinessException ex) {
        log.warn("출금 거절(비즈니스 사유): code={}, msg={}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus()).body(EntityModel.of(toResponse(ex)));
    }

    @ExceptionHandler(SystemException.class)
    public ResponseEntity<EntityModel<DebitResponse>> handleSystem(SystemException ex) {
        log.error("출금 처리 중 시스템 오류: code={}, msg={}", ex.getErrorCode(), ex.getMessage(), ex);
        return ResponseEntity.status(ex.getHttpStatus()).body(EntityModel.of(toResponse(ex)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<EntityModel<DebitResponse>> handleUnknown(Exception ex) {
        log.error("예상하지 못한 오류", ex);
        DebitResponse response = DebitResponse.builder()
                .success(false)
                .responseCode("96")
                .responseMessage("시스템 오류가 발생했습니다")
                .build();
        return ResponseEntity.internalServerError().body(EntityModel.of(response));
    }

    private DebitResponse toResponse(DomainException ex) {
        return DebitResponse.builder()
                .success(false)
                .accountNumber(ex.getAccountNumber())
                .amount(ex.getAmount())
                .responseCode(ex.getErrorCode())
                .responseMessage(ex.getMessage())
                .build();
    }
}