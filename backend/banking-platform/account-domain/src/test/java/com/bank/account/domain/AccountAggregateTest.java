package com.bank.account.domain;

import com.bank.sharedkernel.domain.Currency;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountAggregateTest {

    @Test
    void should_open_account_and_emit_opened_event() {
        AccountId accountId = AccountId.newId();
        CustomerId customerId = new CustomerId("CUST-001");

        AccountAggregate aggregate = AccountAggregate.open(
                accountId,
                customerId,
                AccountType.CHECKING,
                Currency.EUR,
                "PROD-001",
                "corr-1"
        );

        assertThat(aggregate.getUncommittedEvents())
                .hasSize(1)
                .first()
                .isInstanceOf(AccountOpenedEvent.class);

        AccountOpenedEvent event = (AccountOpenedEvent) aggregate.getUncommittedEvents().getFirst();
        assertThat(event.accountId()).isEqualTo(accountId.value());
        assertThat(event.customerId()).isEqualTo("CUST-001");
        assertThat(aggregate.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void should_freeze_and_unfreeze_account() {
        AccountAggregate aggregate = openActiveAccount();

        aggregate.freeze("suspicious activity", "corr-2", "caus-1");
        assertThat(aggregate.getStatus()).isEqualTo(AccountStatus.FROZEN);

        aggregate.unfreeze("cleared", "corr-3", "caus-2");
        assertThat(aggregate.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void should_reject_freeze_when_not_active() {
        AccountAggregate aggregate = openActiveAccount();
        aggregate.freeze("suspicious activity", "corr-2", "caus-1");

        assertThatThrownBy(() -> aggregate.freeze("again", "corr-3", "caus-2"))
                .isInstanceOf(AccountFrozenException.class);
    }

    @Test
    void should_reject_unfreeze_when_not_frozen() {
        AccountAggregate aggregate = openActiveAccount();

        assertThatThrownBy(() -> aggregate.unfreeze("no", "corr-4", "caus-3"))
                .isInstanceOf(InvalidAccountStateException.class);
    }

    @Test
    void should_close_account_from_active_or_frozen() {
        AccountAggregate active = openActiveAccount();
        active.close("customer request", "corr-5", "caus-4");
        assertThat(active.getStatus()).isEqualTo(AccountStatus.CLOSED);

        AccountAggregate frozen = openActiveAccount();
        frozen.freeze("risk", "corr-6", "caus-5");
        frozen.close("customer request", "corr-7", "caus-6");
        assertThat(frozen.getStatus()).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    void should_reject_close_when_already_closed() {
        AccountAggregate aggregate = openActiveAccount();
        aggregate.close("customer request", "corr-8", "caus-7");

        assertThatThrownBy(() -> aggregate.close("again", "corr-9", "caus-8"))
                .isInstanceOf(AccountAlreadyClosedException.class);
    }

    private AccountAggregate openActiveAccount() {
        return AccountAggregate.open(
                AccountId.newId(),
                new CustomerId("CUST-002"),
                AccountType.SAVINGS,
                Currency.USD,
                "PROD-002",
                "corr-0"
        );
    }
}
