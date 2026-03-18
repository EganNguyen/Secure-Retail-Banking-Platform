package com.bank.transfer.domain;

import com.bank.sharedkernel.domain.Currency;
import com.bank.sharedkernel.domain.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferInitiatedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        String causationId,
        String transferId,
        String sourceAccountId,
        String destinationAccountId,
        String beneficiaryName,
        BigDecimal amount,
        Currency currency,
        String reference
) implements DomainEvent {
}
