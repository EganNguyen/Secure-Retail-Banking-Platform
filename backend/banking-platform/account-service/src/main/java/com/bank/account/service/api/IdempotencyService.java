package com.bank.account.service.api;

import com.bank.transfer.infrastructure.idempotency.IdempotencyRecordEntity;
import com.bank.transfer.infrastructure.idempotency.IdempotencyRecordJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class IdempotencyService {
    private final IdempotencyRecordJpaRepository repository;
    private final Clock clock;

    @Autowired
    public IdempotencyService(IdempotencyRecordJpaRepository repository) {
        this(repository, Clock.systemUTC());
    }

    public IdempotencyService(IdempotencyRecordJpaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Optional<IdempotencyRecordEntity> find(String key) {
        return repository.findById(key);
    }

    @Transactional
    public IdempotencyRecordEntity begin(String key, String requestHash, String method, String path) {
        Instant now = Instant.now(clock);
        if (repository.existsById(key)) {
            throw new IdempotencyConflictException("Idempotency key is already in use");
        }
        return repository.save(new IdempotencyRecordEntity(key, requestHash, method, path, null, null, false, now, now));
    }

    @Transactional
    public void complete(String key, int statusCode, String responseBody) {
        IdempotencyRecordEntity entity = repository.findById(key)
                .orElseThrow(() -> new IdempotencyConflictException("Idempotency key was not initialized"));
        entity.complete(statusCode, responseBody, Instant.now(clock));
        repository.save(entity);
    }
}
