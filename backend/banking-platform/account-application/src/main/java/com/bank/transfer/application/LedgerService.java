package com.bank.transfer.application;

import com.bank.transfer.application.command.CreateTransferCommand;
import com.bank.transfer.domain.TransferId;

public interface LedgerService {
    LedgerPostingResult postInternalTransfer(TransferId transferId, CreateTransferCommand command);
}
