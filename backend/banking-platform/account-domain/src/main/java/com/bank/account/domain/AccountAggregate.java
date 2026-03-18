package com.bank.account.domain;

import com.bank.sharedkernel.domain.AggregateRoot;
import com.bank.sharedkernel.domain.Currency;
import com.bank.sharedkernel.domain.DomainEvent;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class AccountAggregate extends AggregateRoot {
    private AccountId accountId;
    private CustomerId customerId;
    private AccountType type;
    private Currency currency;
    private String productCode;
    private AccountStatus status;

    private final Clock clock;

    public AccountAggregate() {
        this(Clock.systemUTC());
    }

    public AccountAggregate(Clock clock) {
        this.clock = clock;
    }

    public static AccountAggregate open(AccountId accountId,
                                        CustomerId customerId,
                                        AccountType type,
                                        Currency currency,
                                        String productCode,
                                        String correlationId) {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(productCode, "productCode");

        AccountAggregate aggregate = new AccountAggregate();
        aggregate.apply(new AccountOpenedEvent(
                UUID.randomUUID(),
                Instant.now(aggregate.clock),
                correlationId,
                null,
                accountId.value(),
                customerId.value(),
                type,
                currency,
                productCode
        ));
        return aggregate;
    }

    public void freeze(String reason, String correlationId, String causationId) {
        requireActive();
        apply(new AccountFrozenEvent(
                UUID.randomUUID(),
                Instant.now(clock),
                correlationId,
                causationId,
                accountId.value(),
                reason
        ));
    }

    public void unfreeze(String reason, String correlationId, String causationId) {
        if (status != AccountStatus.FROZEN) {
            throw new InvalidAccountStateException("Account must be frozen to unfreeze. Current status: " + status);
        }
        apply(new AccountUnfrozenEvent(
                UUID.randomUUID(),
                Instant.now(clock),
                correlationId,
                causationId,
                accountId.value(),
                reason
        ));
    }

    public void close(String reason, String correlationId, String causationId) {
        if (status == AccountStatus.CLOSED) {
            throw new AccountAlreadyClosedException(accountId.value());
        }
        if (status != AccountStatus.ACTIVE && status != AccountStatus.FROZEN) {
            throw new InvalidAccountStateException("Account must be active or frozen to close. Current status: " + status);
        }
        apply(new AccountClosedEvent(
                UUID.randomUUID(),
                Instant.now(clock),
                correlationId,
                causationId,
                accountId.value(),
                reason
        ));
    }

    private void requireActive() {
        if (status == AccountStatus.FROZEN) {
            throw new AccountFrozenException(accountId.value());
        }
        if (status != AccountStatus.ACTIVE) {
            throw new InvalidAccountStateException("Account must be active. Current status: " + status);
        }
    }

    @Override
    protected void handle(DomainEvent event) {
        if (event instanceof AccountOpenedEvent opened) {
            this.accountId = new AccountId(opened.accountId());
            this.customerId = new CustomerId(opened.customerId());
            this.type = opened.type();
            this.currency = opened.currency();
            this.productCode = opened.productCode();
            this.status = AccountStatus.ACTIVE;
        } else if (event instanceof AccountFrozenEvent) {
            this.status = AccountStatus.FROZEN;
        } else if (event instanceof AccountUnfrozenEvent) {
            this.status = AccountStatus.ACTIVE;
        } else if (event instanceof AccountClosedEvent) {
            this.status = AccountStatus.CLOSED;
        } else {
            throw new IllegalArgumentException("Unsupported event: " + event.getClass().getSimpleName());
        }
    }

    public AccountId getAccountId() {
        return accountId;
    }

    public CustomerId getCustomerId() {
        return customerId;
    }

    public AccountType getType() {
        return type;
    }

    public Currency getCurrency() {
        return currency;
    }

    public String getProductCode() {
        return productCode;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void loadFromHistory(java.util.List<DomainEvent> events) {
        rehydrate(events);
    }
}
