package com.bank.controller;

import com.bank.common.enums.AccountStatus;
import com.bank.entity.Account;
import com.bank.entity.Transaction;
import com.bank.repository.AccountRepository;
import com.bank.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Comparator;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class BankConsoleController {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @GetMapping("/")
    public String console(Model model) {
        List<Account> accounts = accountRepository.findAll();
        List<Transaction> all = transactionRepository.findAll();

        List<Transaction> transactions = all.stream()
                .sorted(Comparator.comparing(Transaction::getTransactionDate).reversed())
                .limit(50)
                .toList();

        long cancelCount = all.stream()
                .filter(t -> t.getReferenceId() != null && t.getReferenceId().startsWith("CANCEL-"))
                .count();

        model.addAttribute("accounts", accounts);
        model.addAttribute("transactions", transactions);
        model.addAttribute("accountCount", accounts.size());
        model.addAttribute("activeCount",
                accounts.stream().filter(a -> a.getAccountStatus() == AccountStatus.ACTIVE).count());
        model.addAttribute("totalBalance",
                accounts.stream().mapToLong(Account::getAmount).sum());
        model.addAttribute("txCount", all.size());
        model.addAttribute("withdrawCount", all.size() - cancelCount);
        model.addAttribute("cancelCount", cancelCount);
        return "bank-console";
    }
}
