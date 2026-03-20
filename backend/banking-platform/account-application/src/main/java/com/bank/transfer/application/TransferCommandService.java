package com.bank.transfer.application;

import com.bank.transfer.application.command.CreateTransferCommand;
import com.bank.transfer.domain.TransferAggregate;
import com.bank.transfer.domain.TransferId;
import org.springframework.stereotype.Service;

@Service
public class TransferCommandService {

    private final TransferEventSourcedRepository repository;

    public TransferCommandService(TransferEventSourcedRepository repository) {
        this.repository = repository;
    }

    public TransferAggregate initiateTransfer(CreateTransferCommand command) {
        TransferAggregate aggregate = TransferAggregate.initiate(
                TransferId.newId(),
                command.sourceAccountId(),
                command.destinationAccountId(),
                command.beneficiaryName(),
                new com.bank.sharedkernel.domain.Money(command.amount(), command.currency()),
                command.reference(),
                command.correlationId()
        );

        // Expected Version is -1 for new aggregates (appending to empty stream)
        repository.save(aggregate, -1L);

        return aggregate;
    }
}
