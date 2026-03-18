package com.bank.account.infrastructure.eventstore;

import com.bank.account.domain.AccountClosedEvent;
import com.bank.account.domain.AccountFrozenEvent;
import com.bank.account.domain.AccountOpenedEvent;
import com.bank.account.domain.AccountUnfrozenEvent;
import com.bank.sharedkernel.domain.DomainEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.HashMap;
import java.util.Map;

public class DomainEventSerializer {
    private final ObjectMapper objectMapper;
    private final Map<String, Class<? extends DomainEvent>> typeMap = new HashMap<>();

    public DomainEventSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy().registerModule(new JavaTimeModule());
        register("AccountOpenedEvent", AccountOpenedEvent.class);
        register("AccountFrozenEvent", AccountFrozenEvent.class);
        register("AccountUnfrozenEvent", AccountUnfrozenEvent.class);
        register("AccountClosedEvent", AccountClosedEvent.class);
    }

    private void register(String type, Class<? extends DomainEvent> clazz) {
        typeMap.put(type, clazz);
    }

    public String toJson(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize event " + event.getClass().getSimpleName(), ex);
        }
    }

    public DomainEvent fromJson(String eventType, String json) {
        Class<? extends DomainEvent> clazz = typeMap.get(eventType);
        if (clazz == null) {
            throw new IllegalArgumentException("Unknown event type: " + eventType);
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize event " + eventType, ex);
        }
    }
}
