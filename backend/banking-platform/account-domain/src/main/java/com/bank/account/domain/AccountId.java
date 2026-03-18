package com.bank.account.domain;

import java.util.Objects;
import java.util.UUID;

public record AccountId(String value) {
    public AccountId {
        Objects.requireNonNull(value, "value");
    }

    public static AccountId newId() {
        return new AccountId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
