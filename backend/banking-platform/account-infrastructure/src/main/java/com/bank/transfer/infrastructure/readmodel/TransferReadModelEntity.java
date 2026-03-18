package com.bank.transfer.infrastructure.readmodel;

import com.bank.sharedkernel.domain.Currency;
import com.bank.transfer.domain.TransferFailureReason;
import com.bank.transfer.domain.TransferStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transfer_read_model")
public class TransferReadModelEntity {
    @Id
    @Column(name = "transfer_id")
    private String transferId;

    @Column(name = "source_account_id", nullable = false)
    private String sourceAccountId;

    @Column(name = "destination_account_id", nullable = false)
    private String destinationAccountId;

    @Column(name = "beneficiary_name", nullable = false)
    private String beneficiaryName;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private Currency currency;

    @Column(name = "reference")
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransferStatus status;

    @Column(name = "ledger_reference")
    private String ledgerReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason")
    private TransferFailureReason failureReason;

    @Column(name = "failure_detail")
    private String failureDetail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TransferReadModelEntity() {
    }

    public TransferReadModelEntity(String transferId,
                                   String sourceAccountId,
                                   String destinationAccountId,
                                   String beneficiaryName,
                                   BigDecimal amount,
                                   Currency currency,
                                   String reference,
                                   TransferStatus status,
                                   String ledgerReference,
                                   TransferFailureReason failureReason,
                                   String failureDetail,
                                   Instant createdAt,
                                   Instant updatedAt) {
        this.transferId = transferId;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.beneficiaryName = beneficiaryName;
        this.amount = amount;
        this.currency = currency;
        this.reference = reference;
        this.status = status;
        this.ledgerReference = ledgerReference;
        this.failureReason = failureReason;
        this.failureDetail = failureDetail;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getTransferId() {
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

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
