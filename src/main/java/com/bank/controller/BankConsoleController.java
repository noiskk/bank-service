package com.bank.controller;

import com.bank.entity.Transaction;
import com.bank.repository.AccountRepository;
import com.bank.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Comparator;
import java.util.List;

/**
 * 은행 관제 화면 (시연용).
 * 결제가 일어날 때 계좌 잔액이 실제로 줄고, 취소되면 복구되는 것을 눈으로 확인할 수 있다.
 */
@Controller
@RequiredArgsConstructor
public class BankConsoleController {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @GetMapping("/")
    public String console(Model model) {
        model.addAttribute("accounts", accountRepository.findAll());

        List<Transaction> transactions = transactionRepository.findAll().stream()
                .sorted(Comparator.comparing(Transaction::getTransactionDate).reversed())
                .limit(30)
                .toList();
        model.addAttribute("transactions", transactions);
        return "bank-console";
    }
}
