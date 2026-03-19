package com.bank.transfer.infrastructure.ledger;

import com.bank.transfer.application.BalanceProjectionRepository;
import com.bank.transfer.application.BalanceView;
import com.bank.transfer.application.LedgerPostingResult;
import com.bank.transfer.application.LedgerService;
import com.bank.transfer.application.command.CreateTransferCommand;
import com.bank.transfer.domain.TransferId;
import com.bank.transfer.domain.TransferValidationException;
import com.bank.transfer.infrastructure.balance.BalanceProjectionEntity;
import com.bank.transfer.infrastructure.balance.BalanceProjectionJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class LedgerServiceImpl implements LedgerService {
    private final BalanceProjectionRepository balanceProjectionRepository;
    private final BalanceProjectionJpaRepository balanceProjectionJpaRepository;
    private final LedgerEntryJpaRepository ledgerEntryJpaRepository;
    private final Clock clock;
    private final int maxRetries;
    @Autowired
    public LedgerServiceImpl(BalanceProjectionRepository balanceProjectionRepository,
                             BalanceProjectionJpaRepository balanceProjectionJpaRepository,
                             LedgerEntryJpaRepository ledgerEntryJpaRepository,
                             @Value("${transfer.optimistic-lock.max-retries:3}") int maxRetries) {
        this(balanceProjectionRepository, balanceProjectionJpaRepository, ledgerEntryJpaRepository, Clock.systemUTC(), maxRetries);
    }
    
    public LedgerServiceImpl(BalanceProjectionRepository balanceProjectionRepository,
                             BalanceProjectionJpaRepository balanceProjectionJpaRepository,
                             LedgerEntryJpaRepository ledgerEntryJpaRepository,
                             Clock clock,
                             int maxRetries) {
        this.balanceProjectionRepository = balanceProjectionRepository;
        this.balanceProjectionJpaRepository = balanceProjectionJpaRepository;
        this.ledgerEntryJpaRepository = ledgerEntryJpaRepository;
        this.clock = clock;
        this.maxRetries = maxRetries;
    }

    @Override
    @Transactional
    public LedgerPostingResult postInternalTransfer(TransferId transferId, CreateTransferCommand command) {
        ObjectOptimisticLockingFailureException lastConflict = null;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                return postTransferOnce(transferId, command);
            } catch (ObjectOptimisticLockingFailureException ex) {
                lastConflict = ex;
            }
        }
        throw lastConflict != null ? lastConflict : new ObjectOptimisticLockingFailureException(LedgerEntryEntity.class, transferId.value());
    }

    private LedgerPostingResult postTransferOnce(TransferId transferId, CreateTransferCommand command) {
        Instant now = Instant.now(clock);
        BalanceProjectionEntity sourceEntity = balanceProjectionJpaRepository.findById(command.sourceAccountId())
                .orElseGet(() -> balanceProjectionJpaRepository.save(new BalanceProjectionEntity(command.sourceAccountId(), BigDecimal.ZERO.setScale(4), command.currency(), now)));
        BalanceProjectionEntity destinationEntity = balanceProjectionJpaRepository.findById(command.destinationAccountId())
                .orElseGet(() -> balanceProjectionJpaRepository.save(new BalanceProjectionEntity(command.destinationAccountId(), BigDecimal.ZERO.setScale(4), command.currency(), now)));

        if (sourceEntity.getAvailableBalance().compareTo(command.amount()) < 0) {
            throw new TransferValidationException("Insufficient funds for transfer");
        }

        BalanceView updatedSource = balanceProjectionRepository.upsert(
                sourceEntity.getAccountId(),
                sourceEntity.getCurrency(),
                sourceEntity.getAvailableBalance().subtract(command.amount()),
                sourceEntity.getVersion(),
                now
        );
        BalanceView updatedDestination = balanceProjectionRepository.upsert(
                destinationEntity.getAccountId(),
                destinationEntity.getCurrency(),
                destinationEntity.getAvailableBalance().add(command.amount()),
                destinationEntity.getVersion(),
                now
        );

        String ledgerReference = "LEDGER-" + transferId.value().substring(0, 8).toUpperCase();
        ledgerEntryJpaRepository.save(new LedgerEntryEntity(
                UUID.randomUUID(),
                transferId.value(),
                command.sourceAccountId(),
                LedgerEntryType.DEBIT,
                command.amount(),
                command.currency(),
                now,
                ledgerReference
        ));
        ledgerEntryJpaRepository.save(new LedgerEntryEntity(
                UUID.randomUUID(),
                transferId.value(),
                command.destinationAccountId(),
                LedgerEntryType.CREDIT,
                command.amount(),
                command.currency(),
                now,
                ledgerReference
        ));
        return new LedgerPostingResult(ledgerReference, updatedSource, updatedDestination);
    }
}







