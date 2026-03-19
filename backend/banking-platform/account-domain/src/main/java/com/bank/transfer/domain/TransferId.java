package com.bank.transfer.domain;

import java.util.Objects;
import java.util.UUID;

public record TransferId(String value) {
    public TransferId {
        Objects.requireNonNull(value, "value");
    }

    public static TransferId newId() {
        return new TransferId(UUID.randomUUID().toString());
    }
}
