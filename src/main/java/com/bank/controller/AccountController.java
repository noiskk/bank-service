package com.bank.controller;

import com.bank.dto.CancelRequest;
import com.bank.dto.CancelResponse;
import com.bank.dto.DebitRequest;
import com.bank.dto.DebitResponse;
import com.bank.entity.Account;
import com.bank.exception.InsufficientBalanceException;
import com.bank.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * 계좌 컨트롤러
 * 잔액 조회 및 출금 처리 엔드포인트 제공
 * 요구사항: 4.2, 4.6
 */
@Tag(name = "계좌 관리", description = "계좌 잔액 조회 및 출금 처리 API")
@RestController
@RequestMapping("/api/bank/accounts") // 기획서 스펙에 맞춰 's' 추가
@RequiredArgsConstructor
@Slf4j
public class AccountController {

    private final AccountService accountService;

    @Operation(summary = "출금 처리", description = "카드 번호로 연결된 계좌에서 금액을 출금합니다.")
    @PostMapping("/withdraw")
    public ResponseEntity<EntityModel<DebitResponse>> processDebit(@RequestBody DebitRequest request) {
        log.info("출금 요청 수신: cardNum={}, amount={}", request.getCardNum(), request.getAmount());

        // [Step 1] 카드 번호로 연동된 계좌 조회
        Account account = accountService.findAccountByCardNumber(request.getCardNum());
        String accountNum = account.getAccountNum();

        // [Step 2] 잔액 조회 및 출금 가능 여부 사전 검증
        AccountService.BalanceCheckResult balanceResult = accountService.checkBalance(accountNum, request.getAmount());
        if (!balanceResult.isCanWithdraw()) {
            throw new InsufficientBalanceException(accountNum, request.getAmount());
        }

        // [Step 3] 실제 출금 처리 (비관적 락 적용됨)
        var debitResult = accountService.processDebit(accountNum, request.getAmount(), request.getTransactionId());

        // 응답 생성
        DebitResponse response = DebitResponse.builder()
                .success(debitResult.isSuccess())
                .transactionId(debitResult.getTransactionId())
                .accountNumber(debitResult.getAccountNumber())
                .amount(debitResult.getAmount())
                .balanceAfter(debitResult.getBalanceAfter())
                .transactionDate(debitResult.getTransactionDate())
                .responseCode("00")
                .responseMessage("출금 성공")
                .build();

        log.info("출금 성공: transactionId={}, accountNumber={}, amount={}",
                response.getTransactionId(), response.getAccountNumber(), response.getAmount());

        // HATEOAS 적용 (Self Link 추가)
        EntityModel<DebitResponse> entityModel = EntityModel.of(response);
        WebMvcLinkBuilder selfLink = linkTo(methodOn(AccountController.class).processDebit(request));
        entityModel.add(selfLink.withSelfRel());

        return ResponseEntity.ok(entityModel);
    }

    @Operation(summary = "출금 취소(망취소)",
            description = "원거래 ID로 출금을 되돌립니다. 같은 원거래를 여러 번 요청해도 한 번만 반영됩니다.")
    @PostMapping("/cancel")
    public ResponseEntity<CancelResponse> cancelDebit(@RequestBody CancelRequest request) {
        log.info("출금 취소 요청 수신: transactionId={}, amount={}", request.getTransactionId(), request.getAmount());

        var result = accountService.cancelDebit(request.getTransactionId());

        CancelResponse response = CancelResponse.builder()
                .success(result.isSuccess())
                .transactionId(request.getTransactionId())
                .balanceAfter(result.getBalanceAfter())
                .originalFound(result.isOriginalFound())
                .responseCode("00")
                .responseMessage(result.getMessage())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * 중복되는 에러 응답 객체 생성을 위한 헬퍼 메서드
     */
    private ResponseEntity<EntityModel<DebitResponse>> buildErrorResponse(String code, String message, HttpStatus status) {
        DebitResponse errorResponse = DebitResponse.builder()
                .success(false)
                .responseCode(code)
                .responseMessage(message)
                .build();

        // 에러 응답에도 일관성을 위해 EntityModel로 감싸서 반환
        return ResponseEntity.status(status).body(EntityModel.of(errorResponse));
    }
}