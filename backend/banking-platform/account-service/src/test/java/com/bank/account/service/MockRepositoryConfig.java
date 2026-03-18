package com.bank.account.service;

import com.bank.account.domain.AccountAggregate;
import com.bank.sharedkernel.domain.EventSourcedRepository;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class MockRepositoryConfig {

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public EventSourcedRepository<AccountAggregate> eventSourcedRepository() {
        return Mockito.mock(EventSourcedRepository.class);
    }
}
