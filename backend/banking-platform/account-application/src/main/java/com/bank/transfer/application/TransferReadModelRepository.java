package com.bank.transfer.application;

import java.util.Optional;

public interface TransferReadModelRepository {
    Optional<TransferReadModel> findByTransferId(String transferId);

    TransferReadModel save(TransferReadModel readModel);
}
