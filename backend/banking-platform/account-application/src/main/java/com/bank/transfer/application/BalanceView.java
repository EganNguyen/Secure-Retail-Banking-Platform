package com.bank.transfer.application;

import com.bank.sharedkernel.domain.Currency;

import java.math.BigDecimal;
import java.time.Instant;

public record BalanceView(
        String accountId,
        BigDecimal availableBalance,
        Currency currency,
        Instant updatedAt
) {
}
