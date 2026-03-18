package com.bank.account.infrastructure.eventstore;

import com.bank.account.domain.AccountAggregate;
import com.bank.sharedkernel.domain.DomainEvent;
import com.bank.sharedkernel.domain.EventSourcedRepository;
import com.eventstore.dbclient.AppendToStreamOptions;
import com.eventstore.dbclient.EventData;
import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.ExpectedRevision;
import com.eventstore.dbclient.ReadStreamOptions;
import com.eventstore.dbclient.ResolvedEvent;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class EventStoreDBAccountRepository implements EventSourcedRepository<AccountAggregate> {
    private final EventStoreDBClient client;
    private final DomainEventSerializer serializer;

    public EventStoreDBAccountRepository(EventStoreDBClient client, DomainEventSerializer serializer) {
        this.client = client;
        this.serializer = serializer;
    }

    @Override
    public void save(AccountAggregate aggregate, long expectedVersion) {
        String streamName = streamName(aggregate.getAccountId().value());
        List<EventData> eventData = new ArrayList<>();
        for (DomainEvent event : aggregate.getUncommittedEvents()) {
            String json = serializer.toJson(event);
            eventData.add(EventData.builderAsJson(event.getClass().getSimpleName(), json).build());
        }

        AppendToStreamOptions options = AppendToStreamOptions.get()
                .expectedRevision(expectedVersion == -1
                        ? ExpectedRevision.noStream()
                        : ExpectedRevision.expectedRevision(expectedVersion));

        try {
            client.appendToStream(streamName, options, eventData.iterator())
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to append to stream " + streamName, ex);
        }
        aggregate.markEventsAsCommitted();
    }

    @Override
    public AccountAggregate load(String aggregateId) {
        return loadOptional(aggregateId)
                .orElseThrow(() -> new IllegalStateException("Account not found: " + aggregateId));
    }

    @Override
    public Optional<AccountAggregate> loadOptional(String aggregateId) {
        String streamName = streamName(aggregateId);
        ReadStreamOptions options = ReadStreamOptions.get().fromStart();
        try {
            List<DomainEvent> events = client.readStream(streamName, options)
                    .get()
                    .getEvents()
                    .stream()
                    .map(this::mapEvent)
                    .toList();

            if (events.isEmpty()) {
                return Optional.empty();
            }
            AccountAggregate aggregate = new AccountAggregate();
            aggregate.loadFromHistory(events);
            return Optional.of(aggregate);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read stream " + streamName, ex);
        }
    }

    private DomainEvent mapEvent(ResolvedEvent resolvedEvent) {
        String eventType = resolvedEvent.getOriginalEvent().getEventType();
        String json = new String(resolvedEvent.getOriginalEvent().getEventData());
        return serializer.fromJson(eventType, json);
    }

    private String streamName(String accountId) {
        return "account-" + accountId.toLowerCase();
    }
}
