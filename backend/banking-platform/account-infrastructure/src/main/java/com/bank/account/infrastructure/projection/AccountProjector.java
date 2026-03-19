package com.bank.account.infrastructure.projection;

import com.bank.account.domain.AccountClosedEvent;
import com.bank.account.domain.AccountFrozenEvent;
import com.bank.account.domain.AccountOpenedEvent;
import com.bank.account.domain.AccountUnfrozenEvent;
import com.bank.account.infrastructure.eventstore.DomainEventSerializer;
import com.bank.account.infrastructure.readmodel.AccountReadModelEntity;
import com.bank.account.infrastructure.readmodel.AccountReadModelJpaRepository;
import com.bank.sharedkernel.domain.DomainEvent;
import com.bank.transfer.application.BalanceProjectionRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class AccountProjector {
    private final DomainEventSerializer serializer;
    private final AccountReadModelJpaRepository repository;
    private final BalanceProjectionRepository balanceRepository; // Added field

    public AccountProjector(DomainEventSerializer serializer,
                            AccountReadModelJpaRepository repository,
                            BalanceProjectionRepository balanceRepository) { // Added parameter
        this.serializer = serializer;
        this.repository = repository;
        this.balanceRepository = balanceRepository; // Initialized field
    }

    @KafkaListener(
            topics = "${kafka.topics.account-events:account-events}",
            groupId = "${kafka.consumer.group:account-service-account}"
    )
    @Transactional
    public void onEvent(@Payload String payload,
                        @Header("eventType") String eventType) {
        DomainEvent event = serializer.fromJson(eventType, payload);

        if (event instanceof AccountOpenedEvent opened) {
            AccountReadModelEntity entity = new AccountReadModelEntity(
                    opened.accountId(),
                    opened.customerId(),
                    opened.type(),
                    opened.currency(),
                    opened.productCode(),
                    com.bank.account.domain.AccountStatus.ACTIVE,
                    opened.occurredAt(),
                    opened.occurredAt()
            );
            repository.save(entity);
            
            // Initialize balance only if it doesn't exist
            if (balanceRepository.findByAccountId(opened.accountId()).isEmpty()) {
                try {
                    balanceRepository.saveNew(opened.accountId(), opened.currency(), BigDecimal.ZERO.setScale(4), opened.occurredAt());
                } catch (Exception e) {
                    // Ignore if already created by another thread
                }
            }
        } else if (event instanceof AccountFrozenEvent frozen) {
            updateStatus(frozen.accountId(), com.bank.account.domain.AccountStatus.FROZEN, frozen.occurredAt());
        } else if (event instanceof AccountUnfrozenEvent unfrozen) {
            updateStatus(unfrozen.accountId(), com.bank.account.domain.AccountStatus.ACTIVE, unfrozen.occurredAt());
        } else if (event instanceof AccountClosedEvent closed) {
            updateStatus(closed.accountId(), com.bank.account.domain.AccountStatus.CLOSED, closed.occurredAt());
        }
    }

    private void updateStatus(String accountId, com.bank.account.domain.AccountStatus status, Instant updatedAt) {
        repository.findById(accountId).ifPresent(entity -> {
            entity.updateStatus(status, updatedAt);
            repository.save(entity);
        });
    }
}
