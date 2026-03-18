package com.bank.account.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {

    @Query("select o from OutboxMessage o where o.status = com.bank.account.infrastructure.outbox.OutboxStatus.PENDING order by o.occurredAt asc")
    List<OutboxMessage> findPending();
}
