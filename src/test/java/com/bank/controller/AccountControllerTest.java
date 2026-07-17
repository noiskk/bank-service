package com.bank.controller;

import com.bank.dto.DebitRequest;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.GlobalExceptionHandler;
import com.bank.exception.InsufficientBalanceException;
import com.bank.exception.InvalidRequestException;
import com.bank.service.AccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AccountController가 예외를 직접 안 잡아도(try-catch 제거됨)
 * GlobalExceptionHandler가 실제 HTTP 응답을 올바르게 조립하는지 검증한다.
 *
 * 특히 잔액부족(InsufficientBalanceException)이 HTTP 200으로 응답되는지가 핵심 -
 * 예전에는 422로 응답해서 card-payment-service의 Feign 호출이 이걸 예외로 오인했었다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountController - 예외처리 통합 테스트")
class AccountControllerTest {

    @Mock
    private AccountService accountService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AccountController controller = new AccountController(accountService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("잔액부족(BusinessException) -> HTTP 200 + responseCode 51 (Feign 오인 버그 수정 검증)")
    void insufficientBalance_returns200WithCode51() throws Exception {
        DebitRequest request = DebitRequest.builder().cardNum("1111").amount(999_999L).build();

        when(accountService.findAccountByCardNumber(anyString()))
                .thenThrow(new InsufficientBalanceException("acc-1", 999_999L));

        mockMvc.perform(post("/api/bank/accounts/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCode").value("51"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("계좌 없음(BusinessException) -> HTTP 200 + responseCode 14")
    void accountNotFound_returns200WithCode14() throws Exception {
        DebitRequest request = DebitRequest.builder().cardNum("0000").amount(10000L).build();

        when(accountService.findAccountByCardNumber(anyString()))
                .thenThrow(new AccountNotFoundException("계좌를 찾을 수 없습니다: 0000"));

        mockMvc.perform(post("/api/bank/accounts/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCode").value("14"));
    }

    @Test
    @DisplayName("입력값 오류(BusinessException) -> HTTP 400 (진짜 malformed request만 400 유지)")
    void invalidRequest_returns400() throws Exception {
        DebitRequest request = DebitRequest.builder().cardNum("").amount(10000L).build();

        when(accountService.findAccountByCardNumber(anyString()))
                .thenThrow(new InvalidRequestException("카드 번호는 필수입니다"));

        mockMvc.perform(post("/api/bank/accounts/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.responseCode").value("96"));
    }
}
