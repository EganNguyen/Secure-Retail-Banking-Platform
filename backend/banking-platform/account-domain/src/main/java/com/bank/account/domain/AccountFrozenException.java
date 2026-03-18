package com.bank.account.domain;

public class AccountFrozenException extends AccountDomainException {
    public AccountFrozenException(String accountId) {
        super("Account is frozen: " + accountId);
    }
}
