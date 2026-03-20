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
     * 카드 번호
     */
    @Schema(description = "카드 번호", example = "1234123412341234", requiredMode = Schema.RequiredMode.REQUIRED)
    private String cardNum;

    /**
     * 출금 금액
     */
    @Schema(description = "출금 금액", example = "50000", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long amount;
}
