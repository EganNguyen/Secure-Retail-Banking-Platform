package com.bank.account.domain;

public class InvalidAccountStateException extends AccountDomainException {
    public InvalidAccountStateException(String message) {
        super(message);
    }
}
