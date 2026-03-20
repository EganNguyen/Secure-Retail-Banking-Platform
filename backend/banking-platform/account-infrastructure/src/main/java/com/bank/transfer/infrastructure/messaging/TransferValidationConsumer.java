package com.bank.transfer.infrastructure.messaging;

import com.bank.account.infrastructure.eventstore.DomainEventSerializer;
import com.bank.transfer.application.TransferEventSourcedRepository;
import com.bank.transfer.application.TransferValidationService;
import com.bank.transfer.application.command.CreateTransferCommand;
import com.bank.transfer.domain.TransferAggregate;
import com.bank.transfer.domain.TransferFailureReason;
import com.bank.transfer.domain.TransferInitiatedEvent;
import com.bank.transfer.domain.TransferStatus;
import com.bank.transfer.domain.TransferValidationException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class TransferValidationConsumer {

    private final TransferEventSourcedRepository repository;
    private final TransferValidationService validationService;
    private final DomainEventSerializer serializer;
    private final com.bank.account.infrastructure.messaging.KafkaIdempotencyStore idempotencyStore;

    public TransferValidationConsumer(TransferEventSourcedRepository repository,
                                      TransferValidationService validationService,
                                      DomainEventSerializer serializer,
                                      com.bank.account.infrastructure.messaging.KafkaIdempotencyStore idempotencyStore) {
        this.repository = repository;
        this.validationService = validationService;
        this.serializer = serializer;
        this.idempotencyStore = idempotencyStore;
    }

    @KafkaListener(topics = "${kafka.topics.account-events:account-events}", groupId = "${kafka.consumer.group:account-service-account}")
    public void onEvent(ConsumerRecord<String, String> record) {
        byte[] typeBytes = record.headers().lastHeader("eventType") != null
                ? record.headers().lastHeader("eventType").value() : null;

        if (typeBytes == null) return;
        String eventType = new String(typeBytes, StandardCharsets.UTF_8);

        if ("TransferInitiatedEvent".equals(eventType)) {
            TransferInitiatedEvent event = (TransferInitiatedEvent) serializer.fromJson(eventType, record.value());
            String eventIdStr = event.eventId().toString();
            String consumerGroup = "account-service-account-validation";
            
            if (!idempotencyStore.checkAndAcquire(eventIdStr, consumerGroup)) {
                return; // Already processed or in-flight
            }

            try {
                String transferId = event.transferId();
                TransferAggregate aggregate;
                try {
                    aggregate = repository.load(transferId);
                } catch (Exception e) {
                    idempotencyStore.release(eventIdStr, consumerGroup);
                    return; // Not found yet
                }

                if (aggregate.getStatus() != TransferStatus.INITIATED) {
                    idempotencyStore.markCompleted(eventIdStr, consumerGroup);
                    return; // Already processed
                }

                try {
                    validationService.validate(new CreateTransferCommand(
                            event.sourceAccountId(),
                            event.destinationAccountId(),
                            event.beneficiaryName(),
                            event.amount(),
                            event.currency(),
                            event.reference(),
                            event.correlationId(),
                            event.causationId()
                    ));

                    aggregate.markRiskScored(event.correlationId(), eventIdStr);
                    aggregate.markValidated(event.correlationId(), eventIdStr);
                    aggregate.reserveDebit(event.correlationId(), eventIdStr);

                    repository.save(aggregate, -1L);

                } catch (TransferValidationException ex) {
                    TransferFailureReason reason = TransferFailureReason.PROCESSING_ERROR;
                    if (ex.getMessage().contains("AML")) reason = TransferFailureReason.AML_NAME_REJECTED;
                    else if (ex.getMessage().contains("limit")) reason = TransferFailureReason.LIMIT_EXCEEDED;
                    else if (ex.getMessage().contains("active") || ex.getMessage().contains("found")) reason = TransferFailureReason.ACCOUNT_NOT_ACTIVE;
                    
                    aggregate.fail(reason, ex.getMessage(), event.correlationId(), eventIdStr);
                    repository.save(aggregate, -1L);
                } catch (Exception ex) {
                    aggregate.fail(TransferFailureReason.PROCESSING_ERROR, ex.getMessage(), event.correlationId(), eventIdStr);
                    repository.save(aggregate, -1L);
                }
                
                // Mark success processing for idempotency store
                idempotencyStore.markCompleted(eventIdStr, consumerGroup);

            } catch (Exception e) {
                // If anything fails entirely outside normal saga flow, release lock so it can be retried
                idempotencyStore.release(eventIdStr, consumerGroup);
                throw e; // Bubble up for Kafka retry/DLQ
            }
        }
    }
}
