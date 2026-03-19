package com.bank.account.service.api;

import com.bank.transfer.application.TransferQueryService;
import com.bank.transfer.application.TransferSagaOrchestrator;
import com.bank.transfer.application.command.CreateTransferCommand;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.bank.account.service.api.TransferRequests.CreateInternalTransferRequest;
import static com.bank.account.service.api.TransferResponses.BalanceResponse;
import static com.bank.account.service.api.TransferResponses.TransferResponse;

@RestController
@RequestMapping("/api/v1")
public class TransferController {
    private final TransferSagaOrchestrator orchestrator;
    private final TransferQueryService queryService;

    public TransferController(TransferSagaOrchestrator orchestrator, TransferQueryService queryService) {
        this.orchestrator = orchestrator;
        this.queryService = queryService;
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TransferResponse createInternalTransfer(@Valid @RequestBody CreateInternalTransferRequest request,
                                                   @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
                                                   @RequestHeader(value = "X-Causation-ID", required = false) String causationId) {
        String resolvedCorrelationId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        String resolvedCausationId = causationId != null ? causationId : resolvedCorrelationId;
        return TransferResponse.from(orchestrator.initiateInternalTransfer(new CreateTransferCommand(
                request.sourceAccountId(),
                request.destinationAccountId(),
                request.beneficiaryName(),
                request.amount(),
                request.currency(),
                request.reference(),
                resolvedCorrelationId,
                resolvedCausationId
        )));
    }

    @GetMapping("/transfers/{transferId}")
    public TransferResponse getTransfer(@PathVariable("transferId") String transferId) {
        return TransferResponse.from(queryService.getTransfer(transferId));
    }

    @GetMapping("/accounts/{accountId}/balance")
    public BalanceResponse getBalance(@PathVariable("accountId") String accountId) {
        return BalanceResponse.from(queryService.getBalance(accountId));
    }
}
