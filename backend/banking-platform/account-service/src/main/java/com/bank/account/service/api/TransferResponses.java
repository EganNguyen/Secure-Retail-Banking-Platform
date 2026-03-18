package com.bank.account.service.api;

import com.bank.transfer.application.BalanceView;
import com.bank.transfer.application.TransferReadModel;
import com.bank.transfer.domain.TransferFailureReason;
import com.bank.transfer.domain.TransferStatus;
import com.bank.sharedkernel.domain.Currency;

import java.math.BigDecimal;
import java.time.Instant;

public final class TransferResponses {
    private TransferResponses() {
    }

    public record TransferResponse(
            String transferId,
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
            Instant updatedAt
    ) {
        public static TransferResponse from(TransferReadModel model) {
            return new TransferResponse(
                    model.transferId(),
                    model.sourceAccountId(),
                    model.destinationAccountId(),
                    model.beneficiaryName(),
                    model.amount(),
                    model.currency(),
                    model.reference(),
                    model.status(),
                    model.ledgerReference(),
                    model.failureReason(),
                    model.failureDetail(),
                    model.createdAt(),
                    model.updatedAt()
            );
        }
    }

    public record BalanceResponse(
            String accountId,
            BigDecimal availableBalance,
            Currency currency,
            Instant updatedAt
    ) {
        public static BalanceResponse from(BalanceView balanceView) {
            return new BalanceResponse(
                    balanceView.accountId(),
                    balanceView.availableBalance(),
                    balanceView.currency(),
                    balanceView.updatedAt()
            );
        }
    }
}
