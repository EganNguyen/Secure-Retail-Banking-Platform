package com.bank.transfer.infrastructure.balance;

import com.bank.sharedkernel.domain.Currency;
import com.bank.transfer.application.BalanceProjectionRepository;
import com.bank.transfer.application.BalanceView;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Repository
@Primary
public class CachedBalanceProjectionRepository implements BalanceProjectionRepository {
    private final BalanceProjectionRepositoryAdapter delegate;
    private final BalanceCache balanceCache;

    public CachedBalanceProjectionRepository(BalanceProjectionRepositoryAdapter delegate, BalanceCache balanceCache) {
        this.delegate = delegate;
        this.balanceCache = balanceCache;
    }

    @Override
    public Optional<BalanceView> findByAccountId(String accountId) {
        Optional<BalanceView> cached = balanceCache.get(accountId);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<BalanceView> loaded = delegate.findByAccountId(accountId);
        loaded.ifPresent(balanceCache::put);
        return loaded;
    }

    @Override
    public BalanceView saveNew(String accountId, Currency currency, BigDecimal openingBalance, Instant updatedAt) {
        BalanceView saved = delegate.saveNew(accountId, currency, openingBalance, updatedAt);
        balanceCache.put(saved);
        return saved;
    }

    @Override
    public BalanceView upsert(String accountId, Currency currency, BigDecimal availableBalance, long expectedVersion, Instant updatedAt) {
        BalanceView saved = delegate.upsert(accountId, currency, availableBalance, expectedVersion, updatedAt);
        balanceCache.put(saved);
        return saved;
    }
}
