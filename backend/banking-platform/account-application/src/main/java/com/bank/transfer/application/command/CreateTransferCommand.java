package com.bank.transfer.application.command;

import com.bank.sharedkernel.domain.Currency;

import java.math.BigDecimal;

public record CreateTransferCommand(
        String sourceAccountId,
        String destinationAccountId,
        String beneficiaryName,
        BigDecimal amount,
        Currency currency,
        String reference,
        String correlationId,
        String causationId
) {
}
