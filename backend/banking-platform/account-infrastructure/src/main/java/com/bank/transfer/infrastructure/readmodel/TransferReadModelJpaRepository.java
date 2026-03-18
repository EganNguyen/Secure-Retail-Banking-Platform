package com.bank.transfer.infrastructure.readmodel;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferReadModelJpaRepository extends JpaRepository<TransferReadModelEntity, String> {
}
