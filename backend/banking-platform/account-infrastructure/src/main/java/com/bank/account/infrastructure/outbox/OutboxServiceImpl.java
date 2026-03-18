package com.bank.account.infrastructure.outbox;

import com.bank.account.application.OutboxService;
import com.bank.account.infrastructure.eventstore.DomainEventSerializer;
import com.bank.sharedkernel.domain.DomainEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxServiceImpl implements OutboxService {
    private final OutboxRepository repository;
    private final DomainEventSerializer serializer;

    public OutboxServiceImpl(OutboxRepository repository, DomainEventSerializer serializer) {
        this.repository = repository;
        this.serializer = serializer;
    }

    @Override
    @Transactional
    public void enqueue(String aggregateId, List<DomainEvent> events) {
        for (DomainEvent event : events) {
            OutboxMessage message = new OutboxMessage(
                    UUID.randomUUID(),
                    aggregateId,
                    event.getClass().getSimpleName(),
                    serializer.toJson(event),
                    OutboxStatus.PENDING,
                    Instant.now()
            );
            repository.save(message);
        }
    }
}
