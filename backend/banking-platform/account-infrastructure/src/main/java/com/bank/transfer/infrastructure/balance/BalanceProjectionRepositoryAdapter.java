package com.bank.transfer.infrastructure.balance;

import com.bank.sharedkernel.domain.Currency;
import com.bank.transfer.application.BalanceProjectionRepository;
import com.bank.transfer.application.BalanceView;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Repository
public class BalanceProjectionRepositoryAdapter implements BalanceProjectionRepository {
    private final BalanceProjectionJpaRepository repository;

    public BalanceProjectionRepositoryAdapter(BalanceProjectionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<BalanceView> findByAccountId(String accountId) {
        return repository.findById(accountId).map(this::toView);
    }

    @Override
    @Transactional
    public BalanceView saveNew(String accountId, Currency currency, BigDecimal openingBalance, Instant updatedAt) {
        return toView(repository.save(new BalanceProjectionEntity(accountId, openingBalance, currency, updatedAt)));
    }

    @Override
    @Transactional
    public BalanceView upsert(String accountId, Currency currency, BigDecimal availableBalance, long expectedVersion, Instant updatedAt) {
        BalanceProjectionEntity entity = repository.findById(accountId)
                .orElseGet(() -> repository.save(new BalanceProjectionEntity(accountId, BigDecimal.ZERO.setScale(4), currency, updatedAt)));
        if (entity.getVersion() != expectedVersion) {
            throw new ObjectOptimisticLockingFailureException(BalanceProjectionEntity.class, accountId);
        }
        entity.update(availableBalance, updatedAt);
        return toView(repository.saveAndFlush(entity));
    }

    private BalanceView toView(BalanceProjectionEntity entity) {
        return new BalanceView(
                entity.getAccountId(),
                entity.getAvailableBalance(),
                entity.getCurrency(),
                entity.getUpdatedAt()
        );
    }
}
