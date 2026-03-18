package com.bank.transfer.application;

import com.bank.transfer.application.command.CreateTransferCommand;

public interface TransferValidationService {
    TransferValidationResult validate(CreateTransferCommand command);
}
