package com.zubova.paymentaudit.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zubova.paymentaudit.aop.PaymentAuditAspect;
import com.zubova.paymentaudit.config.AuditFailureStrategy;
import com.zubova.paymentaudit.config.PaymentAuditProperties;
import com.zubova.paymentaudit.event.publisher.KafkaPaymentEventPublisher;
import com.zubova.paymentaudit.event.publisher.PaymentEventPublisher;
import com.zubova.paymentaudit.payload.AuditPayloadProcessor;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PaymentAuditAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PaymentAuditAutoConfiguration.class));

    @Test
    void shouldNotCreateAuditBeansWhenAuditIsDisabled() {
        contextRunner
                .withPropertyValues(
                        "app.audit.payment.enabled=false"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(PaymentAuditProperties.class);
                    assertThat(context).doesNotHaveBean(PaymentEventPublisher.class);
                });
    }

    @Test
    void shouldBindDefaultPropertiesWhenAuditIsEnabled() {
        contextRunner
                .withPropertyValues(
                        "app.audit.payment.enabled=true"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(PaymentAuditProperties.class);

                    PaymentAuditProperties properties = context.getBean(PaymentAuditProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.isLogRequestBody()).isTrue();
                    assertThat(properties.isLogResponseBody()).isTrue();
                    assertThat(properties.isMaskSensitiveData()).isTrue();
                    assertThat(properties.getSensitiveFields()).isEmpty();
                    assertThat(properties.getMaxBodySize()).isEqualTo(10_000);
                    assertThat(properties.getEventPublisherType()).isEqualTo(PaymentAuditProperties.EventPublisherType.KAFKA);
                    assertThat(properties.getFailureStrategy()).isEqualTo(AuditFailureStrategy.FAIL_OPEN);
                });
    }

    @Test
    void shouldCreateKafkaPublisherWhenKafkaTemplateIsAvailable() {
        contextRunner
                .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
                .withPropertyValues(
                        "app.audit.payment.enabled=true",
                        "app.audit.payment.event-publisher-type=kafka"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(PaymentEventPublisher.class);
                    assertThat(context).hasSingleBean(KafkaPaymentEventPublisher.class);
                });
    }

    @Test
    void shouldBindCustomPropertiesWhenConfigured() {
        contextRunner
                .withPropertyValues(
                        "app.audit.payment.enabled=true",
                        "app.audit.payment.log-request-body=false",
                        "app.audit.payment.log-response-body=false",
                        "app.audit.payment.mask-sensitive-data=false",
                        "app.audit.payment.sensitive-fields=card,passport",
                        "app.audit.payment.max-body-size=5000",
                        "app.audit.payment.event-publisher-type=kafka",
                        "app.audit.payment.failure-strategy=fail-closed"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(PaymentAuditProperties.class);

                    PaymentAuditProperties properties = context.getBean(PaymentAuditProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.isLogRequestBody()).isFalse();
                    assertThat(properties.isLogResponseBody()).isFalse();
                    assertThat(properties.isMaskSensitiveData()).isFalse();
                    assertThat(properties.getSensitiveFields()).isEqualTo(List.of("card", "passport"));
                    assertThat(properties.getMaxBodySize()).isEqualTo(5000);
                    assertThat(properties.getEventPublisherType()).isEqualTo(PaymentAuditProperties.EventPublisherType.KAFKA);
                    assertThat(properties.getFailureStrategy()).isEqualTo(AuditFailureStrategy.FAIL_CLOSED);
                });
    }

    @Test
    void shouldBackOffWhenCustomPaymentEventPublisherExists() {
        contextRunner
                .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
                .withBean(PaymentEventPublisher.class, () -> mock(PaymentEventPublisher.class))
                .withPropertyValues(
                        "app.audit.payment.enabled=true",
                        "app.audit.payment.event-publisher-type=kafka"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(PaymentEventPublisher.class);
                    assertThat(context).doesNotHaveBean(KafkaPaymentEventPublisher.class);
                });
    }

    @Test
    void shouldCreateAuditPayloadProcessorWhenObjectMapperIsAvailable() {
        contextRunner
                .withBean(ObjectMapper.class, () -> mock(ObjectMapper.class))
                .withPropertyValues(
                        "app.audit.payment.enabled=true"
                )
                .run(context -> assertThat(context).hasSingleBean(AuditPayloadProcessor.class));
    }

    @Test
    void shouldBackOffWhenCustomAuditPayloadProcessorExists() {
        contextRunner
                .withBean(ObjectMapper.class, () -> mock(ObjectMapper.class))
                .withBean(AuditPayloadProcessor.class, () -> mock(AuditPayloadProcessor.class))
                .withPropertyValues(
                        "app.audit.payment.enabled=true"
                )
                .run(context -> assertThat(context).hasSingleBean(AuditPayloadProcessor.class));
    }

    @Test
    void shouldCreatePaymentAuditAspectWhenNeededBeansAreAvailable() {
        contextRunner
                .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
                .withBean(ObjectMapper.class, () -> mock(ObjectMapper.class))
                .withBean(MeterRegistry.class, () -> mock(MeterRegistry.class))
                .withPropertyValues(
                        "app.audit.payment.enabled=true"
                )
                .run(context -> assertThat(context).hasSingleBean(PaymentAuditAspect.class));
    }

    @Test
    void shouldBackOffWhenCustomPaymentAuditAspectExists() {
        contextRunner
                .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
                .withBean(ObjectMapper.class, () -> mock(ObjectMapper.class))
                .withBean(MeterRegistry.class, () -> mock(MeterRegistry.class))
                .withBean(PaymentAuditAspect.class, () -> mock(PaymentAuditAspect.class))
                .withPropertyValues(
                        "app.audit.payment.enabled=true"
                )
                .run(context -> assertThat(context).hasSingleBean(AuditPayloadProcessor.class));
    }

}