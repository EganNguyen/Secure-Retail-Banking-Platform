package com.bank.transfer.application;

import com.bank.transfer.application.command.CreateTransferCommand;
import com.bank.transfer.domain.TransferAggregate;
import com.bank.transfer.domain.TransferFailureReason;
import com.bank.transfer.domain.TransferId;
import com.bank.transfer.domain.TransferValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class TransferSagaOrchestrator {
    private final TransferEventSourcedRepository repository;
    private final TransferReadModelRepository transferReadModelRepository;
    private final TransferValidationService validationService;
    private final LedgerService ledgerService;
    private final TransferNotificationService notificationService;
    private final Clock clock;
    @Autowired
    public TransferSagaOrchestrator(TransferEventSourcedRepository repository,
                                    TransferReadModelRepository transferReadModelRepository,
                                    TransferValidationService validationService,
                                    LedgerService ledgerService,
                                    TransferNotificationService notificationService) {
        this(repository, transferReadModelRepository, validationService, ledgerService, notificationService, Clock.systemUTC());
    }
    
    public TransferSagaOrchestrator(TransferEventSourcedRepository repository,
                                    TransferReadModelRepository transferReadModelRepository,
                                    TransferValidationService validationService,
                                    LedgerService ledgerService,
                                    TransferNotificationService notificationService,
                                    Clock clock) {
        this.repository = repository;
        this.transferReadModelRepository = transferReadModelRepository;
        this.validationService = validationService;
        this.ledgerService = ledgerService;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Transactional
    public TransferReadModel initiateInternalTransfer(CreateTransferCommand command) {
        TransferId transferId = TransferId.newId();
        TransferAggregate aggregate = TransferAggregate.initiate(
                transferId,
                command.sourceAccountId(),
                command.destinationAccountId(),
                command.beneficiaryName(),
                new com.bank.sharedkernel.domain.Money(command.amount(), command.currency()),
                command.reference(),
                command.correlationId()
        );
        repository.save(aggregate, aggregate.getVersion());

        TransferReadModel readModel = saveReadModel(aggregate, Instant.now(clock));
        notificationService.transferUpdated(readModel);

        try {
            validationService.validate(command);
            aggregate.markValidated(command.correlationId(), command.causationId());
            repository.save(aggregate, aggregate.getVersion());
            readModel = saveReadModel(aggregate, Instant.now(clock));
            notificationService.transferUpdated(readModel);

            LedgerPostingResult postingResult = ledgerService.postInternalTransfer(transferId, command);
            aggregate.markLedgerPosted(postingResult.ledgerReference(), command.correlationId(), command.causationId());
            repository.save(aggregate, aggregate.getVersion());
            readModel = saveReadModel(aggregate, Instant.now(clock));
            notificationService.transferUpdated(readModel);
            notificationService.balanceUpdated(postingResult.sourceBalance());
            notificationService.balanceUpdated(postingResult.destinationBalance());

            aggregate.complete(command.correlationId(), command.causationId());
            repository.save(aggregate, aggregate.getVersion());
            readModel = saveReadModel(aggregate, Instant.now(clock));
            notificationService.transferUpdated(readModel);
            return readModel;
        } catch (TransferValidationException ex) {
            return failTransfer(aggregate, TransferFailureReason.PROCESSING_ERROR, ex.getMessage(), command);
        } catch (RuntimeException ex) {
            TransferFailureReason reason = ex.getClass().getSimpleName().contains("Optimistic")
                    ? TransferFailureReason.CONCURRENCY_CONFLICT
                    : TransferFailureReason.PROCESSING_ERROR;
            return failTransfer(aggregate, reason, ex.getMessage(), command);
        }
    }

    private TransferReadModel failTransfer(TransferAggregate aggregate,
                                           TransferFailureReason reason,
                                           String detail,
                                           CreateTransferCommand command) {
        aggregate.fail(reason, detail, command.correlationId(), command.causationId());
        repository.save(aggregate, aggregate.getVersion());
        TransferReadModel readModel = saveReadModel(aggregate, Instant.now(clock));
        notificationService.transferUpdated(readModel);
        if (reason == TransferFailureReason.PROCESSING_ERROR || reason == TransferFailureReason.CONCURRENCY_CONFLICT) {
            throw new TransferValidationException(detail);
        }
        return readModel;
    }

    private TransferReadModel saveReadModel(TransferAggregate aggregate, Instant updatedAt) {
        Instant createdAt = updatedAt;
        return transferReadModelRepository.save(new TransferReadModel(
                aggregate.getTransferId().value(),
                aggregate.getSourceAccountId(),
                aggregate.getDestinationAccountId(),
                aggregate.getBeneficiaryName(),
                aggregate.getAmount().amount(),
                aggregate.getAmount().currency(),
                aggregate.getReference(),
                aggregate.getStatus(),
                aggregate.getLedgerReference(),
                aggregate.getFailureReason(),
                aggregate.getFailureDetail(),
                createdAt,
                updatedAt
        ));
    }
}







