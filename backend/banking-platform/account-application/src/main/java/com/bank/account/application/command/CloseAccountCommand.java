package com.bank.account.application.command;

public record CloseAccountCommand(
        String accountId,
        String reason,
        String correlationId,
        String causationId
) {
}
