package com.bank.transfer.domain;

public enum TransferStatus {
    INITIATED,
    VALIDATED,
    LEDGER_POSTED,
    COMPLETED,
    FAILED
}
