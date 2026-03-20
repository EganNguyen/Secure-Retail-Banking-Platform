package com.bank.transfer.domain;

import com.bank.sharedkernel.domain.Currency;
import com.bank.sharedkernel.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TransferAggregateTest {

    // -----------------------------------------------------------------------
    // Happy-path: full saga lifecycle
    // -----------------------------------------------------------------------

    @Test
    void shouldTransitionThroughInternalTransferSagaStates() {
        TransferAggregate aggregate = TransferAggregate.initiate(
                TransferId.newId(),
                "SRC-1",
                "DST-1",
                "Alice Doe",
                new Money(new BigDecimal("50.0000"), Currency.USD),
                "Rent",
                "corr-1"
        );

        aggregate.markRiskScored("corr-1", "caus-1");
        aggregate.markValidated("corr-1", "caus-1");
        aggregate.reserveDebit("corr-1", "caus-1");
        aggregate.markDebited("corr-1", "caus-1");
        aggregate.markCredited("corr-1", "caus-1");
        aggregate.complete("corr-1", "caus-1");

        assertEquals(TransferStatus.COMPLETED, aggregate.getStatus());
    }

    // -----------------------------------------------------------------------
    // Validation: amount guards
    // -----------------------------------------------------------------------

    @Test
    void shouldRejectZeroAmountTransfer() {
        assertThatThrownBy(() -> TransferAggregate.initiate(
                TransferId.newId(),
                "SRC-1", "DST-1", "Alice",
                new Money(BigDecimal.ZERO, Currency.USD),
                "Ref", "corr-2"
        )).isInstanceOf(TransferValidationException.class);
    }

    @Test
    void shouldRejectNegativeAmountTransfer() {
        assertThatThrownBy(() -> TransferAggregate.initiate(
                TransferId.newId(),
                "SRC-1", "DST-1", "Alice",
                new Money(new BigDecimal("-1.00"), Currency.USD),
                "Ref", "corr-3"
        )).isInstanceOf(TransferValidationException.class);
    }

    // -----------------------------------------------------------------------
    // Failure path
    // -----------------------------------------------------------------------

    @Test
    void shouldFailTransferAndSetFailureReason() {
        TransferAggregate aggregate = initiate();

        aggregate.fail(TransferFailureReason.INSUFFICIENT_FUNDS, "Balance too low", "corr-4", "caus-4");

        assertThat(aggregate.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(aggregate.getFailureReason()).isEqualTo(TransferFailureReason.INSUFFICIENT_FUNDS);
        assertThat(aggregate.getFailureDetail()).isEqualTo("Balance too low");
    }

    @Test
    void shouldRejectFailWhenAlreadyCompleted() {
        TransferAggregate aggregate = initiate();
        aggregate.markRiskScored("corr-5", "caus-5");
        aggregate.markValidated("corr-5", "caus-5");
        aggregate.reserveDebit("corr-5", "caus-5");
        aggregate.markDebited("corr-5", "caus-5");
        aggregate.markCredited("corr-5", "caus-5");
        aggregate.complete("corr-5", "caus-5");

        assertThatThrownBy(() ->
                aggregate.fail(TransferFailureReason.PROCESSING_ERROR, "too late", "corr-6", "caus-6")
        ).isInstanceOf(InvalidTransferStateException.class);
    }

    // -----------------------------------------------------------------------
    // State-guard: invalid state transitions
    // -----------------------------------------------------------------------

    @Test
    void shouldRejectMarkValidatedWhenNotRiskScored() {
        TransferAggregate aggregate = initiate();

        assertThatThrownBy(() -> aggregate.markValidated("corr-7b", "caus-7b"))
                .isInstanceOf(InvalidTransferStateException.class);
    }

    @Test
    void shouldRejectMarkDebitedWhenNotReserved() {
        TransferAggregate aggregate = initiate();

        assertThatThrownBy(() -> aggregate.markDebited("corr-8", "caus-8"))
                .isInstanceOf(InvalidTransferStateException.class);
    }

    @Test
    void shouldRejectCompleteWhenNotCredited() {
        TransferAggregate aggregate = initiate();
        aggregate.markRiskScored("corr-9", "caus-9");
        aggregate.markValidated("corr-9", "caus-9");

        assertThatThrownBy(() -> aggregate.complete("corr-9b", "caus-9b"))
                .isInstanceOf(InvalidTransferStateException.class);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private TransferAggregate initiate() {
        return TransferAggregate.initiate(
                TransferId.newId(),
                "SRC-10", "DST-10", "Bob",
                new Money(new BigDecimal("100.0000"), Currency.USD),
                "TestRef", "corr-0"
        );
    }
}
