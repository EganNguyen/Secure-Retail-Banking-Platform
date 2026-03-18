package com.bank.account.application;

import com.bank.account.application.command.CloseAccountCommand;
import com.bank.account.application.command.FreezeAccountCommand;
import com.bank.account.application.command.OpenAccountCommand;
import com.bank.account.application.command.UnfreezeAccountCommand;
import com.bank.account.domain.AccountAggregate;
import com.bank.account.domain.AccountId;
import com.bank.account.domain.CustomerId;
import com.bank.sharedkernel.domain.DomainEvent;
import com.bank.sharedkernel.domain.EventSourcedRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class AccountCommandService {
    private final EventSourcedRepository<AccountAggregate> repository;
    private final OutboxService outboxService;

    public AccountCommandService(EventSourcedRepository<AccountAggregate> repository,
                                 OutboxService outboxService) {
        this.repository = repository;
        this.outboxService = outboxService;
    }

    @Transactional
    public String open(OpenAccountCommand command) {
        AccountId accountId = AccountId.newId();
        AccountAggregate aggregate = AccountAggregate.open(
                accountId,
                new CustomerId(command.customerId()),
                command.type(),
                command.currency(),
                command.productCode(),
                command.correlationId()
        );

        List<DomainEvent> newEvents = new ArrayList<>(aggregate.getUncommittedEvents());
        repository.save(aggregate, aggregate.getVersion());
        outboxService.enqueue(accountId.value(), newEvents);
        return accountId.value();
    }

    @Transactional
    public void freeze(FreezeAccountCommand command) {
        AccountAggregate aggregate = repository.load(command.accountId());
        aggregate.freeze(command.reason(), command.correlationId(), command.causationId());

        List<DomainEvent> newEvents = new ArrayList<>(aggregate.getUncommittedEvents());
        repository.save(aggregate, aggregate.getVersion());
        outboxService.enqueue(command.accountId(), newEvents);
    }

    @Transactional
    public void unfreeze(UnfreezeAccountCommand command) {
        AccountAggregate aggregate = repository.load(command.accountId());
        aggregate.unfreeze(command.reason(), command.correlationId(), command.causationId());

        List<DomainEvent> newEvents = new ArrayList<>(aggregate.getUncommittedEvents());
        repository.save(aggregate, aggregate.getVersion());
        outboxService.enqueue(command.accountId(), newEvents);
    }

    @Transactional
    public void close(CloseAccountCommand command) {
        AccountAggregate aggregate = repository.load(command.accountId());
        aggregate.close(command.reason(), command.correlationId(), command.causationId());

        List<DomainEvent> newEvents = new ArrayList<>(aggregate.getUncommittedEvents());
        repository.save(aggregate, aggregate.getVersion());
        outboxService.enqueue(command.accountId(), newEvents);
    }
}
