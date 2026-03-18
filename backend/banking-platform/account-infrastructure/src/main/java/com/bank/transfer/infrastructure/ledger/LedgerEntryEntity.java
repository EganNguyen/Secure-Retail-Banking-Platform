package com.bank.transfer.infrastructure.ledger;

import com.bank.sharedkernel.domain.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entry")
public class LedgerEntryEntity {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "transfer_id", nullable = false)
    private String transferId;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private LedgerEntryType entryType;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private Currency currency;

    @Column(name = "booking_time", nullable = false)
    private Instant bookingTime;

    @Column(name = "ledger_reference", nullable = false)
    private String ledgerReference;

    protected LedgerEntryEntity() {
    }

    public LedgerEntryEntity(UUID id, String transferId, String accountId, LedgerEntryType entryType,
                             BigDecimal amount, Currency currency, Instant bookingTime, String ledgerReference) {
        this.id = id;
        this.transferId = transferId;
        this.accountId = accountId;
        this.entryType = entryType;
        this.amount = amount;
        this.currency = currency;
        this.bookingTime = bookingTime;
        this.ledgerReference = ledgerReference;
    }
}
