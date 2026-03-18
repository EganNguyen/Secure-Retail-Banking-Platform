package com.bank.transfer.infrastructure.readmodel;

import com.bank.transfer.application.TransferReadModel;
import com.bank.transfer.application.TransferReadModelRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class TransferReadModelRepositoryAdapter implements TransferReadModelRepository {
    private final TransferReadModelJpaRepository repository;

    public TransferReadModelRepositoryAdapter(TransferReadModelJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<TransferReadModel> findByTransferId(String transferId) {
        return repository.findById(transferId).map(this::toReadModel);
    }

    @Override
    public TransferReadModel save(TransferReadModel readModel) {
        TransferReadModelEntity entity = repository.findById(readModel.transferId())
                .map(existing -> new TransferReadModelEntity(
                        readModel.transferId(),
                        readModel.sourceAccountId(),
                        readModel.destinationAccountId(),
                        readModel.beneficiaryName(),
                        readModel.amount(),
                        readModel.currency(),
                        readModel.reference(),
                        readModel.status(),
                        readModel.ledgerReference(),
                        readModel.failureReason(),
                        readModel.failureDetail(),
                        existing.getCreatedAt(),
                        readModel.updatedAt()
                ))
                .orElseGet(() -> new TransferReadModelEntity(
                        readModel.transferId(),
                        readModel.sourceAccountId(),
                        readModel.destinationAccountId(),
                        readModel.beneficiaryName(),
                        readModel.amount(),
                        readModel.currency(),
                        readModel.reference(),
                        readModel.status(),
                        readModel.ledgerReference(),
                        readModel.failureReason(),
                        readModel.failureDetail(),
                        readModel.createdAt(),
                        readModel.updatedAt()
                ));
        return toReadModel(repository.save(entity));
    }

    private TransferReadModel toReadModel(TransferReadModelEntity entity) {
        return new TransferReadModel(
                entity.getTransferId(),
                entity.getSourceAccountId(),
                entity.getDestinationAccountId(),
                entity.getBeneficiaryName(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getReference(),
                entity.getStatus(),
                entity.getLedgerReference(),
                entity.getFailureReason(),
                entity.getFailureDetail(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
