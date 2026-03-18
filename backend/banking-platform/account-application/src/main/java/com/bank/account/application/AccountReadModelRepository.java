package com.bank.account.application;

import java.util.List;
import java.util.Optional;

public interface AccountReadModelRepository {
    Optional<AccountReadModel> findByAccountId(String accountId);
    List<AccountReadModel> findByCustomerId(String customerId);
}
