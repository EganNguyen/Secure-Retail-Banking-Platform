package com.bank.transfer.infrastructure.messaging;

import com.bank.account.infrastructure.eventstore.DomainEventSerializer;
import com.bank.transfer.application.LedgerPostingResult;
import com.bank.transfer.application.LedgerService;
import com.bank.transfer.application.TransferEventSourcedRepository;
import com.bank.transfer.application.command.CreateTransferCommand;
import com.bank.transfer.domain.TransferAggregate;
import com.bank.transfer.domain.TransferDebitReservedEvent;
import com.bank.transfer.domain.TransferFailureReason;
import com.bank.transfer.domain.TransferStatus;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class TransferLedgerEventConsumer {

    private final TransferEventSourcedRepository repository;
    private final LedgerService ledgerService;
    private final DomainEventSerializer serializer;
    private final com.bank.account.infrastructure.messaging.KafkaIdempotencyStore idempotencyStore;

    public TransferLedgerEventConsumer(TransferEventSourcedRepository repository,
                                       LedgerService ledgerService,
                                       DomainEventSerializer serializer,
                                       com.bank.account.infrastructure.messaging.KafkaIdempotencyStore idempotencyStore) {
        this.repository = repository;
        this.ledgerService = ledgerService;
        this.serializer = serializer;
        this.idempotencyStore = idempotencyStore;
    }

    @KafkaListener(topics = "${kafka.topics.account-events:account-events}", groupId = "${kafka.consumer.group:account-service-account}")
    public void onEvent(ConsumerRecord<String, String> record) {
        byte[] typeBytes = record.headers().lastHeader("eventType") != null
                ? record.headers().lastHeader("eventType").value() : null;

        if (typeBytes == null) return;
        String eventType = new String(typeBytes, StandardCharsets.UTF_8);

        if ("TransferDebitReservedEvent".equals(eventType)) {
            TransferDebitReservedEvent event = (TransferDebitReservedEvent) serializer.fromJson(eventType, record.value());
            String eventIdStr = event.eventId().toString();
            String consumerGroup = "account-service-account-ledger";

            if (!idempotencyStore.checkAndAcquire(eventIdStr, consumerGroup)) {
                return;
            }

            try {
                String transferId = event.transferId();
                TransferAggregate aggregate;
                try {
                    aggregate = repository.load(transferId);
                } catch (Exception e) {
                    idempotencyStore.release(eventIdStr, consumerGroup);
                    return;
                }

                if (aggregate.getStatus() != TransferStatus.DEBIT_RESERVED) {
                    idempotencyStore.markCompleted(eventIdStr, consumerGroup);
                    return; // Already processed
                }

                try {
                    // We recreate the command from aggregate state since the event doesn't carry all the details
                    CreateTransferCommand command = new CreateTransferCommand(
                            aggregate.getSourceAccountId(),
                            aggregate.getDestinationAccountId(),
                            aggregate.getBeneficiaryName(),
                            aggregate.getAmount().amount(),
                            aggregate.getAmount().currency(),
                            aggregate.getReference(),
                            event.correlationId(),
                            event.causationId()
                    );

                    LedgerPostingResult result = ledgerService.postInternalTransfer(aggregate.getTransferId(), command);

                    // Saga Choreography progress
                    aggregate.markDebited(event.correlationId(), eventIdStr);
                    aggregate.markCredited(event.correlationId(), eventIdStr);
                    aggregate.complete(event.correlationId(), eventIdStr);

                    repository.save(aggregate, -1L);

                } catch (Exception ex) {
                    TransferFailureReason reason = TransferFailureReason.PROCESSING_ERROR;
                    if (ex.getMessage() != null && ex.getMessage().contains("Insufficient funds")) {
                        reason = TransferFailureReason.INSUFFICIENT_FUNDS;
                    } else if (ex.getClass().getSimpleName().contains("OptimisticLocking")) {
                        reason = TransferFailureReason.CONCURRENCY_CONFLICT;
                    }
                    
                    aggregate.fail(reason, ex.getMessage(), event.correlationId(), eventIdStr);
                    repository.save(aggregate, -1L);
                }
                
                idempotencyStore.markCompleted(eventIdStr, consumerGroup);
            } catch (Exception e) {
                idempotencyStore.release(eventIdStr, consumerGroup);
                throw e;
            }
        }
    }
}
