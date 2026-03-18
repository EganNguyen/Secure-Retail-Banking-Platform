package com.bank.sharedkernel.domain;

import java.util.Optional;

public interface EventSourcedRepository<T extends AggregateRoot> {
    void save(T aggregate, long expectedVersion);
    T load(String aggregateId);
    Optional<T> loadOptional(String aggregateId);
}
