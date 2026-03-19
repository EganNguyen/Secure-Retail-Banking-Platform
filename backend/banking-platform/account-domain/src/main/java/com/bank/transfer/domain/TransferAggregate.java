package com.bank.transfer.domain;

import com.bank.sharedkernel.domain.AggregateRoot;
import com.bank.sharedkernel.domain.DomainEvent;
import com.bank.sharedkernel.domain.Money;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class TransferAggregate extends AggregateRoot {
    private TransferId transferId;
    private String sourceAccountId;
    private String destinationAccountId;
    private String beneficiaryName;
    private Money amount;
    private String reference;
    private TransferStatus status;
    private String ledgerReference;
    private TransferFailureReason failureReason;
    private String failureDetail;

    private final Clock clock;

    public TransferAggregate() {
        this(Clock.systemUTC());
    }

    public TransferAggregate(Clock clock) {
        this.clock = clock;
    }

    public static TransferAggregate initiate(TransferId transferId,
                                             String sourceAccountId,
                                             String destinationAccountId,
                                             String beneficiaryName,
                                             Money amount,
                                             String reference,
                                             String correlationId) {
        Objects.requireNonNull(transferId, "transferId");
        Objects.requireNonNull(sourceAccountId, "sourceAccountId");
        Objects.requireNonNull(destinationAccountId, "destinationAccountId");
        Objects.requireNonNull(beneficiaryName, "beneficiaryName");
        Objects.requireNonNull(amount, "amount");
        if (amount.isZeroOrNegative()) {
            throw new TransferValidationException("Transfer amount must be positive");
        }

        TransferAggregate aggregate = new TransferAggregate();
        aggregate.apply(new TransferInitiatedEvent(
                UUID.randomUUID(),
                Instant.now(aggregate.clock),
                correlationId,
                null,
                transferId.value(),
                sourceAccountId,
                destinationAccountId,
                beneficiaryName,
                amount.amount(),
                amount.currency(),
                reference
        ));
        return aggregate;
    }

    public void markValidated(String correlationId, String causationId) {
        requireStatus(TransferStatus.INITIATED);
        apply(new TransferValidatedEvent(
                UUID.randomUUID(),
                Instant.now(clock),
                correlationId,
                causationId,
                transferId.value()
        ));
    }

    public void markLedgerPosted(String ledgerReference, String correlationId, String causationId) {
        requireStatus(TransferStatus.VALIDATED);
        apply(new TransferLedgerPostedEvent(
                UUID.randomUUID(),
                Instant.now(clock),
                correlationId,
                causationId,
                transferId.value(),
                ledgerReference
        ));
    }

    public void complete(String correlationId, String causationId) {
        requireStatus(TransferStatus.LEDGER_POSTED);
        apply(new TransferCompletedEvent(
                UUID.randomUUID(),
                Instant.now(clock),
                correlationId,
                causationId,
                transferId.value()
        ));
    }

    public void fail(TransferFailureReason reason, String detail, String correlationId, String causationId) {
        if (status == TransferStatus.COMPLETED || status == TransferStatus.FAILED) {
            throw new InvalidTransferStateException("Transfer is already terminal");
        }
        apply(new TransferFailedEvent(
                UUID.randomUUID(),
                Instant.now(clock),
                correlationId,
                causationId,
                transferId.value(),
                reason,
                detail
        ));
    }

    private void requireStatus(TransferStatus expected) {
        if (status != expected) {
            throw new InvalidTransferStateException("Expected status " + expected + " but was " + status);
        }
    }

    @Override
    protected void handle(DomainEvent event) {
        if (event instanceof TransferInitiatedEvent initiated) {
            this.transferId = new TransferId(initiated.transferId());
            this.sourceAccountId = initiated.sourceAccountId();
            this.destinationAccountId = initiated.destinationAccountId();
            this.beneficiaryName = initiated.beneficiaryName();
            this.amount = new Money(initiated.amount(), initiated.currency());
            this.reference = initiated.reference();
            this.status = TransferStatus.INITIATED;
            this.ledgerReference = null;
            this.failureReason = null;
            this.failureDetail = null;
        } else if (event instanceof TransferValidatedEvent) {
            this.status = TransferStatus.VALIDATED;
        } else if (event instanceof TransferLedgerPostedEvent posted) {
            this.status = TransferStatus.LEDGER_POSTED;
            this.ledgerReference = posted.ledgerReference();
        } else if (event instanceof TransferCompletedEvent) {
            this.status = TransferStatus.COMPLETED;
        } else if (event instanceof TransferFailedEvent failed) {
            this.status = TransferStatus.FAILED;
            this.failureReason = failed.reason();
            this.failureDetail = failed.detail();
        } else {
            throw new IllegalArgumentException("Unsupported event: " + event.getClass().getSimpleName());
        }
    }

    public void loadFromHistory(List<DomainEvent> events) {
        rehydrate(events);
    }

    public TransferId getTransferId() {
        return transferId;
    }

    public String getSourceAccountId() {
        return sourceAccountId;
    }

    public String getDestinationAccountId() {
        return destinationAccountId;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public Money getAmount() {
        return amount;
    }

    public String getReference() {
        return reference;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public String getLedgerReference() {
        return ledgerReference;
    }

    public TransferFailureReason getFailureReason() {
        return failureReason;
    }

    public String getFailureDetail() {
        return failureDetail;
    }
}
