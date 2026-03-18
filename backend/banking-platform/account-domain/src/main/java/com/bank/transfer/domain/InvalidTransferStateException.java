package com.bank.transfer.domain;

public class InvalidTransferStateException extends TransferDomainException {
    public InvalidTransferStateException(String message) {
        super(message);
    }
}
