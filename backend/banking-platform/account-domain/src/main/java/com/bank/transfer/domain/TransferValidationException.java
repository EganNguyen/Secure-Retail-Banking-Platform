package com.bank.transfer.domain;

public class TransferValidationException extends TransferDomainException {
    public TransferValidationException(String message) {
        super(message);
    }
}
