package com.bank.account.infrastructure.readmodel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountReadModelJpaRepository extends JpaRepository<AccountReadModelEntity, String> {
    List<AccountReadModelEntity> findByCustomerId(String customerId);
}
