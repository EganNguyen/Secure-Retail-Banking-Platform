package com.bank.account.domain;

import com.bank.sharedkernel.domain.DomainEvent;

public sealed interface AccountEvent extends DomainEvent
        permits AccountOpenedEvent, AccountFrozenEvent, AccountUnfrozenEvent, AccountClosedEvent {
}
