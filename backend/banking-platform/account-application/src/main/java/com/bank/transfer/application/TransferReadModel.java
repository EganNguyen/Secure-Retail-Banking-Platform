package com.bank.transfer.application;

import com.bank.sharedkernel.domain.Currency;
import com.bank.transfer.domain.TransferFailureReason;
import com.bank.transfer.domain.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferReadModel(
        String transferId,
        String sourceAccountId,
        String destinationAccountId,
        String beneficiaryName,
        BigDecimal amount,
        Currency currency,
        String reference,
        TransferStatus status,
        String ledgerReference,
        TransferFailureReason failureReason,
        String failureDetail,
        Instant createdAt,
        Instant updatedAt
) {
}
