package com.bank.account.domain;

public class AccountAlreadyClosedException extends AccountDomainException {
    public AccountAlreadyClosedException(String accountId) {
        super("Account already closed: " + accountId);
    }
}
