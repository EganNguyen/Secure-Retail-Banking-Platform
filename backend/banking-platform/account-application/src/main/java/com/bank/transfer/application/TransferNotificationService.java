package com.bank.transfer.application;

public interface TransferNotificationService {
    void transferUpdated(TransferReadModel transfer);

    void balanceUpdated(BalanceView balanceView);
}
