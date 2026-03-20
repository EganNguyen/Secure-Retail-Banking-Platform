package com.bank.transfer.domain;

public enum TransferStatus {
    INITIATED,
    RISK_SCORED,
    VALIDATED,
    DEBIT_RESERVED,
    DEBITED,
    CREDITED,
    COMPLETED,
    FAILED,
    REVERSED
}
