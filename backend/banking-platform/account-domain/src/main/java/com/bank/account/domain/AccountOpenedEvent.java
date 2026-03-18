package com.bank.account.domain;

import com.bank.sharedkernel.domain.Currency;

import java.time.Instant;
import java.util.UUID;

public record AccountOpenedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        String causationId,
        String accountId,
        String customerId,
        AccountType type,
        Currency currency,
        String productCode
) implements AccountEvent {
}
