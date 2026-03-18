package com.bank.transfer.application;

public class TransferNotFoundException extends RuntimeException {
    public TransferNotFoundException(String message) {
        super(message);
    }
}
