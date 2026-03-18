package com.bank.account.application;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AccountQueryService {
    private final AccountReadModelRepository repository;

    public AccountQueryService(AccountReadModelRepository repository) {
        this.repository = repository;
    }

    public AccountReadModel getAccount(String accountId) {
        return repository.findByAccountId(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    public List<AccountReadModel> getAccountsForCustomer(String customerId) {
        return repository.findByCustomerId(customerId);
    }
}
