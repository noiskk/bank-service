package com.bank.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 출금 취소(망취소) 응답 DTO.
 */
@Schema(description = "출금 취소 응답")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelResponse {

    private boolean success;

    @Schema(description = "원거래 ID")
    private String transactionId;

    @Schema(description = "취소 후 잔액")
    private Long balanceAfter;

    private String responseCode;

    private String responseMessage;

    @Schema(description = "원거래가 은행에 존재했는지 여부. false면 애초에 출금이 안 된 것이므로 카드사는 거래를 종결하면 된다.")
    private boolean originalFound;
}
