package com.bank.transfer.infrastructure.balance;

import com.bank.sharedkernel.domain.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "balance_projection")
public class BalanceProjectionEntity {
    @Id
    @Column(name = "account_id")
    private String accountId;

    @Column(name = "available_balance", nullable = false)
    private BigDecimal availableBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private Currency currency;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BalanceProjectionEntity() {
    }

    public BalanceProjectionEntity(String accountId, BigDecimal availableBalance, Currency currency, Instant updatedAt) {
        this.accountId = accountId;
        this.availableBalance = availableBalance;
        this.currency = currency;
        this.updatedAt = updatedAt;
    }

    public String getAccountId() {
        return accountId;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public Currency getCurrency() {
        return currency;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(BigDecimal availableBalance, Instant updatedAt) {
        this.availableBalance = availableBalance;
        this.updatedAt = updatedAt;
    }
}
