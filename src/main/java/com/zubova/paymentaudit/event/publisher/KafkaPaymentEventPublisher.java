package com.zubova.paymentaudit.event.publisher;

import com.zubova.paymentaudit.event.PaymentEvent;
import org.springframework.kafka.core.KafkaTemplate;

public class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    public KafkaPaymentEventPublisher(KafkaTemplate<String, PaymentEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(PaymentEvent event) {
        kafkaTemplate.send("payment-audit-events", event.getCorrelationId(), event);
    }

}