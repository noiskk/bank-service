package com.bank.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 출금 취소(망취소) 요청 DTO.
 * 카드사가 승인 후 정합성 문제를 발견했을 때 원거래를 되돌리기 위해 호출한다.
 */
@Schema(description = "출금 취소 요청")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelRequest {

    @Schema(description = "카드 번호", example = "1234123412341234")
    private String cardNum;

    @Schema(description = "취소 금액", example = "50000")
    private Long amount;

    @Schema(description = "원거래 ID (카드사 거래번호) — 중복 취소를 막는 멱등키", example = "550e8400-e29b-41d4-a716-446655440000")
    private String transactionId;
}
