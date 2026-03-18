package com.bank.transfer.application;

import org.springframework.stereotype.Service;

@Service
public class TransferQueryService {
    private final TransferReadModelRepository transferReadModelRepository;
    private final BalanceProjectionRepository balanceProjectionRepository;

    public TransferQueryService(TransferReadModelRepository transferReadModelRepository,
                                BalanceProjectionRepository balanceProjectionRepository) {
        this.transferReadModelRepository = transferReadModelRepository;
        this.balanceProjectionRepository = balanceProjectionRepository;
    }

    public TransferReadModel getTransfer(String transferId) {
        return transferReadModelRepository.findByTransferId(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));
    }

    public BalanceView getBalance(String accountId) {
        return balanceProjectionRepository.findByAccountId(accountId)
                .orElseThrow(() -> new TransferNotFoundException("Balance not found for account " + accountId));
    }
}
