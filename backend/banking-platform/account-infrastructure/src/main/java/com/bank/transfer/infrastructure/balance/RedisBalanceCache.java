package com.bank.transfer.infrastructure.balance;

import com.bank.sharedkernel.domain.Currency;
import com.bank.transfer.application.BalanceView;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Component
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(name = "transfer.balance-cache.type", havingValue = "redis")
public class RedisBalanceCache implements BalanceCache {
    private final StringRedisTemplate redisTemplate;

    public RedisBalanceCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<BalanceView> get(String accountId) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(key(accountId));
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new BalanceView(
                accountId,
                new BigDecimal(values.get("balance").toString()),
                Currency.valueOf(values.get("currency").toString()),
                Instant.parse(values.get("updatedAt").toString())
        ));
    }

    @Override
    public void put(BalanceView balanceView) {
        redisTemplate.opsForHash().putAll(key(balanceView.accountId()), Map.of(
                "balance", balanceView.availableBalance().toPlainString(),
                "currency", balanceView.currency().name(),
                "updatedAt", balanceView.updatedAt().toString()
        ));
    }

    private String key(String accountId) {
        return "balance:" + accountId;
    }
}
