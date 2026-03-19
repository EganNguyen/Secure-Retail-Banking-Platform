package com.bank.account.service.api;

import com.bank.sharedkernel.domain.Currency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class TransferRequests {
    private TransferRequests() {
    }

    public record CreateInternalTransferRequest(
            @NotBlank String sourceAccountId,
            @NotBlank String destinationAccountId,
            @NotBlank @Size(max = 120) String beneficiaryName,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal amount,
            @NotNull Currency currency,
            @Size(max = 255) String reference
    ) {
    }
}
