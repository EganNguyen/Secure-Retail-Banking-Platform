package com.bank.transfer.infrastructure.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface LedgerEntryJpaRepository extends JpaRepository<LedgerEntryEntity, UUID> {
    @Query("""
            select coalesce(sum(le.amount), 0)
            from LedgerEntryEntity le
            where le.accountId = :accountId
              and le.entryType = com.bank.transfer.infrastructure.ledger.LedgerEntryType.DEBIT
              and le.bookingTime between :start and :end
            """)
    BigDecimal sumDebitsForAccountBetween(@Param("accountId") String accountId,
                                          @Param("start") Instant start,
                                          @Param("end") Instant end);
}
