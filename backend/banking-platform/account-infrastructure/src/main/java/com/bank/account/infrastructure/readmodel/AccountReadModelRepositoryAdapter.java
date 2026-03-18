package com.bank.account.infrastructure.readmodel;

import com.bank.account.application.AccountReadModel;
import com.bank.account.application.AccountReadModelRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AccountReadModelRepositoryAdapter implements AccountReadModelRepository {
    private final AccountReadModelJpaRepository repository;

    public AccountReadModelRepositoryAdapter(AccountReadModelJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<AccountReadModel> findByAccountId(String accountId) {
        return repository.findById(accountId).map(this::toModel);
    }

    @Override
    public List<AccountReadModel> findByCustomerId(String customerId) {
        return repository.findByCustomerId(customerId)
                .stream()
                .map(this::toModel)
                .toList();
    }

    private AccountReadModel toModel(AccountReadModelEntity entity) {
        return new AccountReadModel(
                entity.getAccountId(),
                entity.getCustomerId(),
                entity.getType(),
                entity.getCurrency(),
                entity.getProductCode(),
                entity.getStatus(),
                entity.getOpenedAt(),
                entity.getUpdatedAt()
        );
    }
}
