package com.zubova.paymentaudit.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zubova.paymentaudit.config.PaymentAuditProperties;
import com.zubova.paymentaudit.event.PaymentEvent;
import com.zubova.paymentaudit.event.publisher.KafkaPaymentEventPublisher;
import com.zubova.paymentaudit.event.publisher.PaymentEventPublisher;
import com.zubova.paymentaudit.payload.AuditPayloadProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

@AutoConfiguration
@EnableConfigurationProperties(PaymentAuditProperties.class)
@ConditionalOnProperty(prefix = "app.audit.payment", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentAuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(KafkaTemplate.class)
    @ConditionalOnProperty(prefix = "app.audit.payment", name = "event-publisher-type", havingValue = "kafka",
            matchIfMissing = true)
    PaymentEventPublisher paymentEventPublisher(KafkaTemplate<String, PaymentEvent> kafkaTemplate) {
        return new KafkaPaymentEventPublisher(kafkaTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ObjectMapper.class)
    AuditPayloadProcessor auditPayloadProcessor(ObjectMapper objectMapper, PaymentAuditProperties properties) {
        return new AuditPayloadProcessor(objectMapper, properties);
    }

}