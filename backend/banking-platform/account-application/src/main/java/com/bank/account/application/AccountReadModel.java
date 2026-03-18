package com.bank.account.application;

import com.bank.account.domain.AccountStatus;
import com.bank.account.domain.AccountType;
import com.bank.sharedkernel.domain.Currency;

import java.time.Instant;

public record AccountReadModel(
        String accountId,
        String customerId,
        AccountType type,
        Currency currency,
        String productCode,
        AccountStatus status,
        Instant openedAt,
        Instant updatedAt
) {
}
