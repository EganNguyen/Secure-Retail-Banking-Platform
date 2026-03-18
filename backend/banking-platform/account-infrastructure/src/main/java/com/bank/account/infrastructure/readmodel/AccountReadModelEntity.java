package com.bank.account.infrastructure.readmodel;

import com.bank.account.domain.AccountStatus;
import com.bank.account.domain.AccountType;
import com.bank.sharedkernel.domain.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "account_read_model")
public class AccountReadModelEntity {

    @Id
    @Column(name = "account_id")
    private String accountId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private AccountType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private Currency currency;

    @Column(name = "product_code", nullable = false)
    private String productCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountStatus status;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccountReadModelEntity() {
    }

    public AccountReadModelEntity(String accountId,
                                  String customerId,
                                  AccountType type,
                                  Currency currency,
                                  String productCode,
                                  AccountStatus status,
                                  Instant openedAt,
                                  Instant updatedAt) {
        this.accountId = accountId;
        this.customerId = customerId;
        this.type = type;
        this.currency = currency;
        this.productCode = productCode;
        this.status = status;
        this.openedAt = openedAt;
        this.updatedAt = updatedAt;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public AccountType getType() {
        return type;
    }

    public Currency getCurrency() {
        return currency;
    }

    public String getProductCode() {
        return productCode;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateStatus(AccountStatus status, Instant updatedAt) {
        this.status = status;
        this.updatedAt = updatedAt;
    }
}
