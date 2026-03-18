package com.bank.account.service.api;

import com.bank.account.application.AccountReadModel;
import com.bank.account.domain.AccountStatus;
import com.bank.account.domain.AccountType;
import com.bank.sharedkernel.domain.Currency;

import java.time.Instant;
import java.util.List;

public final class AccountResponses {
    private AccountResponses() {
    }

    public record AccountCreatedResponse(String accountId) {
    }

    public record AccountResponse(
            String accountId,
            String customerId,
            AccountType type,
            Currency currency,
            String productCode,
            AccountStatus status,
            Instant openedAt,
            Instant updatedAt
    ) {
        public static AccountResponse from(AccountReadModel model) {
            return new AccountResponse(
                    model.accountId(),
                    model.customerId(),
                    model.type(),
                    model.currency(),
                    model.productCode(),
                    model.status(),
                    model.openedAt(),
                    model.updatedAt()
            );
        }
    }

    public record AccountListResponse(List<AccountResponse> accounts) {
    }
}
