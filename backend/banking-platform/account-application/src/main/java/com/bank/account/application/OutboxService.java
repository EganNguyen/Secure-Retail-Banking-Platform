package com.bank.account.application;

import com.bank.sharedkernel.domain.DomainEvent;

import java.util.List;

public interface OutboxService {
    void enqueue(String aggregateId, List<DomainEvent> events);
}
