package com.bank.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 출금 응답 DTO
 * 요구사항: 4.6
 */
@Schema(description = "출금 응답")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebitResponse {

    /**
     * 성공 여부
     */
    @Schema(description = "성공 여부", example = "true")
    private boolean success;

    /**
     * 거래 고유 번호
     */
    @Schema(description = "거래 고유 번호", example = "TXN20240214123456789")
    private String transactionId;

    /**
     * 계좌 번호
     */
    @Schema(description = "계좌 번호", example = "1002-111-111111")
    private String accountNumber;

    /**
     * 출금 금액
     */
    @Schema(description = "출금 금액", example = "50000")
    private Long amount; // BigDecimal -> Long 수정

    /**
     * 출금 후 잔액
     */
    @Schema(description = "출금 후 잔액", example = "950000")
    private Long balanceAfter; // BigDecimal -> Long 수정

    /**
     * 거래 일시
     */
    @Schema(description = "거래 일시", example = "2024-02-14T12:34:56")
    private LocalDateTime transactionDate;

    /**
     * 응답 코드 (00: 성공, 51: 잔액 부족, 기타: 오류)
     */
    @Schema(description = "응답 코드 (00: 성공, 51: 잔액 부족, 96: 시스템 오류)", example = "00")
    private String responseCode;

    /**
     * 응답 메시지
     */
    @Schema(description = "응답 메시지", example = "출금 성공")
    private String responseMessage;
}