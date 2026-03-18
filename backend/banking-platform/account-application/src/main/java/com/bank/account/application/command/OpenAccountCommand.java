package com.bank.account.application.command;

import com.bank.account.domain.AccountType;
import com.bank.sharedkernel.domain.Currency;

public record OpenAccountCommand(
        String customerId,
        AccountType type,
        Currency currency,
        String productCode,
        String correlationId
) {
}
