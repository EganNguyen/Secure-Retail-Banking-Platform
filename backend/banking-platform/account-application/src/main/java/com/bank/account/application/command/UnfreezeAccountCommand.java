package com.bank.account.application.command;

public record UnfreezeAccountCommand(
        String accountId,
        String reason,
        String correlationId,
        String causationId
) {
}
