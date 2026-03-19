package com.bank.transfer.infrastructure.eventstore;

import com.bank.account.infrastructure.eventstore.DomainEventSerializer;
import com.bank.sharedkernel.domain.DomainEvent;
import com.bank.transfer.application.TransferEventSourcedRepository;
import com.bank.transfer.domain.TransferAggregate;
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
import java.util.concurrent.TimeUnit;

@Repository
public class EventStoreDBTransferRepository implements TransferEventSourcedRepository {
    private final EventStoreDBClient client;
    private final DomainEventSerializer serializer;

    public EventStoreDBTransferRepository(EventStoreDBClient client, DomainEventSerializer serializer) {
        this.client = client;
        this.serializer = serializer;
    }

    @Override
    public void save(TransferAggregate aggregate, long expectedVersion) {
        String streamName = streamName(aggregate.getTransferId().value());
        List<EventData> eventData = new ArrayList<>();
        for (DomainEvent event : aggregate.getUncommittedEvents()) {
            eventData.add(EventData.builderAsJson(event.getClass().getSimpleName(), serializer.toJson(event).getBytes(java.nio.charset.StandardCharsets.UTF_8)).build());
        }

        AppendToStreamOptions options = AppendToStreamOptions.get()
                .expectedRevision(expectedVersion == -1
                        ? ExpectedRevision.noStream()
                        : ExpectedRevision.expectedRevision(expectedVersion));
        try {
            client.appendToStream(streamName, options, eventData.iterator()).get(30, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to append to stream " + streamName, ex);
        }
        aggregate.markEventsAsCommitted();
    }

    @Override
    public TransferAggregate load(String transferId) {
        return loadOptional(transferId)
                .orElseThrow(() -> new IllegalStateException("Transfer not found: " + transferId));
    }

    @Override
    public Optional<TransferAggregate> loadOptional(String transferId) {
        try {
            List<DomainEvent> events = client.readStream(streamName(transferId), ReadStreamOptions.get().fromStart())
                    .get()
                    .getEvents()
                    .stream()
                    .map(this::mapEvent)
                    .toList();

            if (events.isEmpty()) {
                return Optional.empty();
            }

            TransferAggregate aggregate = new TransferAggregate();
            aggregate.loadFromHistory(events);
            return Optional.of(aggregate);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read stream for transfer " + transferId, ex);
        }
    }

    private DomainEvent mapEvent(ResolvedEvent event) {
        return serializer.fromJson(
                event.getOriginalEvent().getEventType(),
                new String(event.getOriginalEvent().getEventData())
        );
    }

    private String streamName(String transferId) {
        return "transfer-" + transferId.toLowerCase();
    }
}
