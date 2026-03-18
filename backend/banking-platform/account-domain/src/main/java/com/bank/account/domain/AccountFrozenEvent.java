package com.bank.account.domain;

import java.time.Instant;
import java.util.UUID;

public record AccountFrozenEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        String causationId,
        String accountId,
        String reason
) implements AccountEvent {
}
