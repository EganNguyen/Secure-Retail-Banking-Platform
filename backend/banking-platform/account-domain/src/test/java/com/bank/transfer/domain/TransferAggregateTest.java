package com.bank.transfer.domain;

import com.bank.sharedkernel.domain.Currency;
import com.bank.sharedkernel.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransferAggregateTest {

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

        aggregate.markValidated("corr-1", "caus-1");
        aggregate.markLedgerPosted("LEDGER-1", "corr-1", "caus-1");
        aggregate.complete("corr-1", "caus-1");

        assertEquals(TransferStatus.COMPLETED, aggregate.getStatus());
        assertEquals("LEDGER-1", aggregate.getLedgerReference());
    }
}
