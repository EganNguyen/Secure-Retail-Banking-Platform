package com.bank.transfer.application;

import com.bank.sharedkernel.domain.Currency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface BalanceProjectionRepository {
    Optional<BalanceView> findByAccountId(String accountId);

    BalanceView saveNew(String accountId, Currency currency, BigDecimal openingBalance, Instant updatedAt);

    BalanceView upsert(String accountId, Currency currency, BigDecimal availableBalance, long expectedVersion, Instant updatedAt);
}
