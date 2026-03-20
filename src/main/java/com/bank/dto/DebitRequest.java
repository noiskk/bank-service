package com.bank.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 출금 요청 DTO
 * 요구사항: 4.6
 */
@Schema(description = "출금 요청")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebitRequest {

    /**
     * 카드 번호 (암호화된 상태)
     */
    @Schema(description = "카드 번호", example = "1234123412341234", requiredMode = Schema.RequiredMode.REQUIRED)
    private String cardNumber;

    /**
     * 출금 금액
     */
    @Schema(description = "출금 금액", example = "50000", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long amount; // BigDecimal -> Long 수정

    /**
     * 참조 ID (승인 번호 등)
     */
    @Schema(description = "참조 ID (승인 번호 등)", example = "AUTH20240214123456")
    private String referenceId;
}