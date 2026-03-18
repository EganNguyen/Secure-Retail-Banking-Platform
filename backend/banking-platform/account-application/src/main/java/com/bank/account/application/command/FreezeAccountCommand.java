package com.bank.account.application.command;

public record FreezeAccountCommand(
        String accountId,
        String reason,
        String correlationId,
        String causationId
) {
}
