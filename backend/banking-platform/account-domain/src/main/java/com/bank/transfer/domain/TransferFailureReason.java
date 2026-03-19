package com.bank.transfer.domain;

public enum TransferFailureReason {
    ACCOUNT_NOT_ACTIVE,
    AML_NAME_REJECTED,
    LIMIT_EXCEEDED,
    INSUFFICIENT_FUNDS,
    CONCURRENCY_CONFLICT,
    PROCESSING_ERROR
}
