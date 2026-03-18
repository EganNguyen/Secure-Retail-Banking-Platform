package com.bank.sharedkernel.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AggregateRoot {
    private final List<DomainEvent> uncommittedEvents = new ArrayList<>();
    private long version = -1;

    protected void apply(DomainEvent event) {
        handle(event);
        uncommittedEvents.add(event);
    }

    protected void rehydrate(List<DomainEvent> events) {
        for (DomainEvent event : events) {
            handle(event);
            version++;
        }
    }

    public List<DomainEvent> getUncommittedEvents() {
        return Collections.unmodifiableList(uncommittedEvents);
    }

    public void markEventsAsCommitted() {
        uncommittedEvents.clear();
    }

    protected abstract void handle(DomainEvent event);

    public long getVersion() {
        return version;
    }
}
