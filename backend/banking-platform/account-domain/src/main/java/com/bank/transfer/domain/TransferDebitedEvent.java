package com.bank.transfer.domain;

import com.bank.sharedkernel.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record TransferDebitedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        String causationId,
        String transferId
) implements DomainEvent {
}
