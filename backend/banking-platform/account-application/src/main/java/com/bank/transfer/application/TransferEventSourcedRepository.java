package com.bank.transfer.application;

import com.bank.transfer.domain.TransferAggregate;

import java.util.Optional;

public interface TransferEventSourcedRepository {
    void save(TransferAggregate aggregate, long expectedVersion);

    TransferAggregate load(String transferId);

    Optional<TransferAggregate> loadOptional(String transferId);
}
