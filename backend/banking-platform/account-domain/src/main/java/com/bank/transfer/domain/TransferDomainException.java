package com.bank.transfer.domain;

public class TransferDomainException extends RuntimeException {
    public TransferDomainException(String message) {
        super(message);
    }
}
