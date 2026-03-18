package com.bank.sharedkernel.domain;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {
    UUID eventId();
    Instant occurredAt();
    String correlationId();
    String causationId();
}
