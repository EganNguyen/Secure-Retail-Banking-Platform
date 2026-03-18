package com.bank.transfer.infrastructure.balance;

import com.bank.transfer.application.BalanceView;

import java.util.Optional;

public interface BalanceCache {
    Optional<BalanceView> get(String accountId);

    void put(BalanceView balanceView);
}
