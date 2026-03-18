package com.bank.account.service.api;

import com.bank.account.application.AccountCommandService;
import com.bank.account.application.AccountQueryService;
import com.bank.account.application.command.CloseAccountCommand;
import com.bank.account.application.command.FreezeAccountCommand;
import com.bank.account.application.command.OpenAccountCommand;
import com.bank.account.application.command.UnfreezeAccountCommand;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.bank.account.service.api.AccountRequests.*;
import static com.bank.account.service.api.AccountResponses.*;

@RestController
@RequestMapping("/api/v1")
public class AccountController {
    private final AccountCommandService commandService;
    private final AccountQueryService queryService;

    public AccountController(AccountCommandService commandService,
                             AccountQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountCreatedResponse openAccount(@Valid @RequestBody OpenAccountRequest request,
                                              @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        String resolvedCorrelationId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        String accountId = commandService.open(new OpenAccountCommand(
                request.customerId(),
                request.type(),
                request.currency(),
                request.productCode(),
                resolvedCorrelationId
        ));
        return new AccountCreatedResponse(accountId);
    }

    @PostMapping("/accounts/{accountId}/freeze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void freezeAccount(@PathVariable String accountId,
                              @Valid @RequestBody FreezeAccountRequest request,
                              @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
                              @RequestHeader(value = "X-Causation-ID", required = false) String causationId) {
        String resolvedCorrelationId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        String resolvedCausationId = causationId != null ? causationId : resolvedCorrelationId;
        commandService.freeze(new FreezeAccountCommand(
                accountId,
                request.reason(),
                resolvedCorrelationId,
                resolvedCausationId
        ));
    }

    @PostMapping("/accounts/{accountId}/unfreeze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void unfreezeAccount(@PathVariable String accountId,
                                @Valid @RequestBody UnfreezeAccountRequest request,
                                @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
                                @RequestHeader(value = "X-Causation-ID", required = false) String causationId) {
        String resolvedCorrelationId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        String resolvedCausationId = causationId != null ? causationId : resolvedCorrelationId;
        commandService.unfreeze(new UnfreezeAccountCommand(
                accountId,
                request.reason(),
                resolvedCorrelationId,
                resolvedCausationId
        ));
    }

    @PostMapping("/accounts/{accountId}/close")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void closeAccount(@PathVariable String accountId,
                             @Valid @RequestBody CloseAccountRequest request,
                             @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
                             @RequestHeader(value = "X-Causation-ID", required = false) String causationId) {
        String resolvedCorrelationId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        String resolvedCausationId = causationId != null ? causationId : resolvedCorrelationId;
        commandService.close(new CloseAccountCommand(
                accountId,
                request.reason(),
                resolvedCorrelationId,
                resolvedCausationId
        ));
    }

    @GetMapping("/accounts/{accountId}")
    public AccountResponse getAccount(@PathVariable String accountId) {
        return AccountResponse.from(queryService.getAccount(accountId));
    }

    @GetMapping("/customers/{customerId}/accounts")
    public AccountListResponse getAccountsForCustomer(@PathVariable String customerId) {
        List<AccountResponse> accounts = queryService.getAccountsForCustomer(customerId)
                .stream()
                .map(AccountResponse::from)
                .toList();
        return new AccountListResponse(accounts);
    }
}
