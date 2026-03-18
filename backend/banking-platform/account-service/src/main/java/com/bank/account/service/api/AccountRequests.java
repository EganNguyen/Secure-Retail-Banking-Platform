package com.bank.account.service.api;

import com.bank.account.domain.AccountType;
import com.bank.sharedkernel.domain.Currency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class AccountRequests {
    private AccountRequests() {
    }

    public record OpenAccountRequest(
            @NotBlank String customerId,
            @NotNull AccountType type,
            @NotNull Currency currency,
            @NotBlank String productCode
    ) {
    }

    public record FreezeAccountRequest(
            @NotBlank String reason
    ) {
    }

    public record UnfreezeAccountRequest(
            @NotBlank String reason
    ) {
    }

    public record CloseAccountRequest(
            @NotBlank String reason
    ) {
    }
}
