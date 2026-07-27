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

    /**
     * 원거래 ID (카드사 거래번호).
     * 은행 거래 내역에 참조로 남겨두면, 나중에 취소·대사 요청이 왔을 때 이 값으로 원거래를 찾을 수 있다.
     */
    @Schema(description = "카드사 거래번호(참조용)", example = "550e8400-e29b-41d4-a716-446655440000")
    private String transactionId;
}
