package com.bank.account.domain;

public class AccountDomainException extends RuntimeException {
    public AccountDomainException(String message) {
        super(message);
    }
}
