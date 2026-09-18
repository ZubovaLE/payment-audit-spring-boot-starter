package com.zubova.paymentaudit.aop;

import com.zubova.paymentaudit.annotation.AuditPayment;
import com.zubova.paymentaudit.config.AuditFailureStrategy;
import com.zubova.paymentaudit.config.PaymentAuditProperties;
import com.zubova.paymentaudit.event.PaymentEvent;
import com.zubova.paymentaudit.event.publisher.PaymentEventPublisher;
import com.zubova.paymentaudit.exception.AuditException;
import com.zubova.paymentaudit.model.PaymentOperation;
import com.zubova.paymentaudit.payload.AuditPayloadProcessor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentAuditAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private AuditPayment auditPayment;

    @Mock
    private PaymentEventPublisher publisher;

    @Mock
    private AuditPayloadProcessor payloadProcessor;

    private PaymentAuditProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private PaymentAuditAspect aspect;

    @BeforeEach
    void setUp() {
        properties = new PaymentAuditProperties();
        properties.setLogRequestBody(false);
        properties.setLogResponseBody(false);

        meterRegistry = new SimpleMeterRegistry();

        aspect = new PaymentAuditAspect(properties, publisher, payloadProcessor, meterRegistry);
    }

    @Test
    void shouldPublishFailedEventOnceAndRethrowBusinessException() throws Throwable {
        IllegalStateException businessException = new IllegalStateException("Insufficient funds");

        when(auditPayment.operation()).thenReturn(PaymentOperation.TRANSFER);
        when(joinPoint.proceed()).thenThrow(businessException);

        assertThatThrownBy(() -> aspect.auditPaymentOperation(joinPoint, auditPayment)).isSameAs(businessException);

        ArgumentCaptor<PaymentEvent> eventCaptor = ArgumentCaptor.forClass(PaymentEvent.class);

        verify(publisher, times(1)).publish(eventCaptor.capture());

        PaymentEvent event = eventCaptor.getValue();

        assertThat(event.getStatus()).isEqualTo(PaymentEvent.Status.FAILED);
        assertThat(event.getError()).isEqualTo("Insufficient funds");
    }

    @Test
    void shouldThrowAuditExceptionWhenPublisherFailsAndStrategyIsFailClosed() throws Throwable {
        properties.setFailureStrategy(AuditFailureStrategy.FAIL_CLOSED);

        RuntimeException publisherException = new RuntimeException("Kafka unavailable");

        when(auditPayment.operation()).thenReturn(PaymentOperation.TRANSFER);
        when(joinPoint.proceed()).thenReturn("success");

        doThrow(publisherException).when(publisher).publish(any(PaymentEvent.class));

        assertThatThrownBy(() -> aspect.auditPaymentOperation(joinPoint, auditPayment))
                .isInstanceOf(AuditException.class)
                .hasCause(publisherException);
        verify(joinPoint).proceed();
        verify(publisher).publish(any(PaymentEvent.class));
    }

    @Test
    void shouldReturnBusinessResultWhenPublisherFailsAndStrategyIsFailOpen() throws Throwable {
        properties.setFailureStrategy(AuditFailureStrategy.FAIL_OPEN);

        RuntimeException publisherException = new RuntimeException("Kafka unavailable");

        when(auditPayment.operation()).thenReturn(PaymentOperation.TRANSFER);
        when(joinPoint.proceed()).thenReturn("success");
        doThrow(publisherException)
                .when(publisher)
                .publish(any(PaymentEvent.class));

        Object result = aspect.auditPaymentOperation(joinPoint, auditPayment);

        assertThat(result).isEqualTo("success");
        verify(joinPoint).proceed();
        verify(publisher).publish(any(PaymentEvent.class));
    }

    @Test
    void shouldPreserveBusinessExceptionWhenFailedEventPublishingAlsoFails() throws Throwable {
        IllegalStateException businessException = new IllegalStateException("Insufficient funds");
        RuntimeException publisherException = new RuntimeException("Kafka unavailable");

        when(auditPayment.operation()).thenReturn(PaymentOperation.TRANSFER);
        when(joinPoint.proceed()).thenThrow(businessException);
        doThrow(publisherException)
                .when(publisher)
                .publish(any(PaymentEvent.class));

        assertThatThrownBy(() ->
                aspect.auditPaymentOperation(joinPoint, auditPayment)
        ).isSameAs(businessException);

        assertThat(businessException.getSuppressed()).containsExactly(publisherException);
        verify(publisher).publish(any(PaymentEvent.class));
    }

}