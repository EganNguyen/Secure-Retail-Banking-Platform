package com.bank.transfer.infrastructure.balance;

import com.bank.transfer.application.BalanceView;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@ConditionalOnProperty(name = "transfer.balance-cache.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryBalanceCache implements BalanceCache {
    private final ConcurrentMap<String, BalanceView> store = new ConcurrentHashMap<>();

    @Override
    public Optional<BalanceView> get(String accountId) {
        return Optional.ofNullable(store.get(accountId));
    }

    @Override
    public void put(BalanceView balanceView) {
        store.put(balanceView.accountId(), balanceView);
    }
}
