package com.bank.transfer.application;

public record LedgerPostingResult(String ledgerReference, BalanceView sourceBalance, BalanceView destinationBalance) {
}
