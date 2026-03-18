package com.bank.transfer.infrastructure.validation;

import com.bank.account.application.AccountReadModel;
import com.bank.account.application.AccountReadModelRepository;
import com.bank.account.domain.AccountStatus;
import com.bank.transfer.application.TransferValidationResult;
import com.bank.transfer.application.TransferValidationService;
import com.bank.transfer.application.command.CreateTransferCommand;
import com.bank.transfer.domain.TransferValidationException;
import com.bank.transfer.infrastructure.ledger.LedgerEntryJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class TransferValidationServiceImpl implements TransferValidationService {
    private final AccountReadModelRepository accountReadModelRepository;
    private final LedgerEntryJpaRepository ledgerEntryJpaRepository;
    private final Clock clock;
    private final BigDecimal perTransferLimit;
    private final BigDecimal dailyLimit;
    private final List<String> blockedNames;
    @Autowired
    public TransferValidationServiceImpl(AccountReadModelRepository accountReadModelRepository,
                                         LedgerEntryJpaRepository ledgerEntryJpaRepository,
                                         @Value("${transfer.limits.per-transfer:10000.0000}") BigDecimal perTransferLimit,
                                         @Value("${transfer.limits.daily:25000.0000}") BigDecimal dailyLimit,
                                         @Value("${transfer.aml.blocked-names:sanctioned entity}") String blockedNames) {
        this(
                accountReadModelRepository,
                ledgerEntryJpaRepository,
                Clock.systemUTC(),
                perTransferLimit,
                dailyLimit,
                java.util.Arrays.stream(blockedNames.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .toList()
        );
    }
    
    public TransferValidationServiceImpl(AccountReadModelRepository accountReadModelRepository,
                                         LedgerEntryJpaRepository ledgerEntryJpaRepository,
                                         Clock clock,
                                         BigDecimal perTransferLimit,
                                         BigDecimal dailyLimit,
                                         List<String> blockedNames) {
        this.accountReadModelRepository = accountReadModelRepository;
        this.ledgerEntryJpaRepository = ledgerEntryJpaRepository;
        this.clock = clock;
        this.perTransferLimit = perTransferLimit;
        this.dailyLimit = dailyLimit;
        this.blockedNames = blockedNames;
    }

    @Override
    public TransferValidationResult validate(CreateTransferCommand command) {
        if (command.sourceAccountId().equals(command.destinationAccountId())) {
            throw new TransferValidationException("Source and destination accounts must be different");
        }
        if (command.amount().compareTo(perTransferLimit) > 0) {
            throw new TransferValidationException("Transfer exceeds per-transfer limit");
        }
        validateAccount(command.sourceAccountId(), "Source");
        validateAccount(command.destinationAccountId(), "Destination");

        String normalizedBeneficiary = command.beneficiaryName().trim().toLowerCase();
        if (blockedNames.stream().map(String::toLowerCase).anyMatch(normalizedBeneficiary::contains)) {
            throw new TransferValidationException("Beneficiary name failed AML screening");
        }

        LocalDate today = LocalDate.now(clock);
        Instant start = today.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        BigDecimal dailyDebits = ledgerEntryJpaRepository.sumDebitsForAccountBetween(command.sourceAccountId(), start, end);
        if (dailyDebits.add(command.amount()).compareTo(dailyLimit) > 0) {
            throw new TransferValidationException("Transfer exceeds daily limit");
        }

        return new TransferValidationResult();
    }

    private void validateAccount(String accountId, String label) {
        AccountReadModel account = accountReadModelRepository.findByAccountId(accountId)
                .orElseThrow(() -> new TransferValidationException(label + " account not found"));
        if (account.status() != AccountStatus.ACTIVE) {
            throw new TransferValidationException(label + " account is not active");
        }
    }
}







