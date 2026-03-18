package com.bank.account.infrastructure.eventstore;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.EventStoreDBClientSettings;
import com.eventstore.dbclient.EventStoreDBConnectionString;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventStoreDBConfig {

    @Bean
    public EventStoreDBClient eventStoreDBClient(
            @Value("${eventstore.connection-string}") String connectionString) {
        EventStoreDBClientSettings settings = EventStoreDBConnectionString.parseOrThrow(connectionString);
        return EventStoreDBClient.create(settings);
    }

    @Bean
    public DomainEventSerializer domainEventSerializer(ObjectMapper objectMapper) {
        return new DomainEventSerializer(objectMapper);
    }
}
