package com.bank.transfer.infrastructure.balance;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BalanceProjectionJpaRepository extends JpaRepository<BalanceProjectionEntity, String> {
}
