package com.bank.account.service;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.EventStoreDBClientSettings;
import com.eventstore.dbclient.EventStoreDBConnectionString;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

@TestConfiguration
public class EventStoreContainerConfig {

    @Bean(destroyMethod = "stop")
    public GenericContainer<?> eventStoreContainer() {
        GenericContainer<?> container = new GenericContainer<>(
                DockerImageName.parse("eventstore/eventstore:24.10.7-alpha-arm64v8")
        )
                .withEnv("EVENTSTORE_INSECURE", "true")
                .withEnv("EVENTSTORE_EXT_IP", "0.0.0.0")
                .withEnv("EVENTSTORE_MEM_DB", "true")
                .withExposedPorts(2113)
                .withStartupTimeout(Duration.ofSeconds(90))
                .waitingFor(
                        Wait.forHttp("/health/live")
                                .forPort(2113)
                                .forStatusCode(204)
                                .withStartupTimeout(Duration.ofSeconds(60))
                );
        container.start();
        return container;
    }

    @Bean
    @Primary
    public EventStoreDBClient eventStoreDBClient(GenericContainer<?> eventStoreContainer) {
        String address = eventStoreContainer.getHost();
        Integer port = eventStoreContainer.getMappedPort(2113);
        String connectionString = String.format("esdb://%s:%d?tls=false", address, port);
        EventStoreDBClientSettings settings = EventStoreDBConnectionString.parseOrThrow(connectionString);
        return EventStoreDBClient.create(settings);
    }
}
