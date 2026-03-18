package com.bank.account.infrastructure.kafka;

import com.bank.account.infrastructure.outbox.OutboxMessage;
import com.bank.account.infrastructure.outbox.OutboxRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class OutboxPublisher {
    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String accountEventsTopic;

    public OutboxPublisher(OutboxRepository repository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           @Value("${kafka.topics.account-events:account-events}") String accountEventsTopic) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.accountEventsTopic = accountEventsTopic;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.delay-ms:100}")
    @Transactional
    public void publishPending() {
        List<OutboxMessage> pending = repository.findPending();
        for (OutboxMessage message : pending) {
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        accountEventsTopic,
                        message.getAggregateId(),
                        message.getPayload()
                );
                record.headers().add("eventType", message.getEventType().getBytes(StandardCharsets.UTF_8));
                kafkaTemplate.send(record).get();
                message.markPublished();
            } catch (Exception ex) {
                message.markFailed(ex.getMessage());
            }
        }
    }
}
