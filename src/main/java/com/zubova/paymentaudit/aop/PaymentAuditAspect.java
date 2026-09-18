package com.zubova.paymentaudit.aop;

import com.zubova.paymentaudit.annotation.AuditPayment;
import com.zubova.paymentaudit.config.AuditFailureStrategy;
import com.zubova.paymentaudit.config.PaymentAuditProperties;
import com.zubova.paymentaudit.event.PaymentEvent;
import com.zubova.paymentaudit.event.publisher.PaymentEventPublisher;
import com.zubova.paymentaudit.exception.AuditException;
import com.zubova.paymentaudit.model.PaymentOperation;
import com.zubova.paymentaudit.payload.AuditPayloadProcessor;
import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Aspect
public class PaymentAuditAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentAuditAspect.class);
    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String USER_ID_KEY = "userId";
    private static final String CLIENT_IP_KEY = "clientIp";
    private final PaymentAuditProperties properties;
    private final PaymentEventPublisher publisher;
    private final AuditPayloadProcessor auditPayloadProcessor;
    private final MeterRegistry meterRegistry;

    public PaymentAuditAspect(PaymentAuditProperties properties, PaymentEventPublisher publisher,
                              AuditPayloadProcessor auditPayloadProcessor, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.publisher = publisher;
        this.auditPayloadProcessor = auditPayloadProcessor;
        this.meterRegistry = meterRegistry;
    }

    @Around("@annotation(auditPayment)")
    public Object auditPaymentOperation(ProceedingJoinPoint pjp, AuditPayment auditPayment) throws Throwable {
        String existingCorrelationId = MDC.get(CORRELATION_ID_KEY);
        boolean createdByStarter = existingCorrelationId == null;
        String correlationId = createdByStarter ? UUID.randomUUID().toString() : existingCorrelationId;

        if (createdByStarter) {
            MDC.put(CORRELATION_ID_KEY, correlationId);
        }

        long startTime = System.currentTimeMillis();

        PaymentEvent event = createEvent(correlationId, auditPayment);
        try {
            executeAuditAction(() -> addRequestBody(event, pjp, auditPayment));

            Object result;
            try {
                result = pjp.proceed();
            } catch (Throwable exception) {
                handleBusinessFailure(auditPayment, exception, event, startTime);
                throw exception;
            }

            markSuccessful(event, startTime);

            executeAuditAction(() -> addResponseBody(event, result, auditPayment));
            executeAuditAction(() -> publishAuditEvent(event));

            recordMetricSafely(auditPayment.operation(), "success", event.getDuration());

            return result;
        } finally {
            if (createdByStarter) {
                MDC.remove(CORRELATION_ID_KEY);
            }
        }
    }

    private PaymentEvent createEvent(String correlationId, AuditPayment auditPayment) {
        PaymentEvent event = new PaymentEvent();
        event.setCorrelationId(correlationId);
        event.setOperation(auditPayment.operation());
        event.setTimestamp(Instant.now());
        event.setUserId(getCurrentUserId());
        event.setIpAddress(getClientIp());
        return event;
    }

    private String getCurrentUserId() {
        return MDC.get(USER_ID_KEY);
    }

    private String getClientIp() {
        return MDC.get(CLIENT_IP_KEY);
    }

    private void executeAuditAction(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException auditException) {
            if (properties.getFailureStrategy() == AuditFailureStrategy.FAIL_CLOSED) {
                throw new AuditException("Payment audit processing failed", auditException);
            }
            LOGGER.error("Audit processing failed", auditException);
        }
    }

    private void addRequestBody(PaymentEvent event, ProceedingJoinPoint pjp, AuditPayment auditPayment) {
        if (properties.isLogRequestBody()) {
            event.setRequestBody(auditPayloadProcessor.process(pjp.getArgs(), auditPayment.excludedFields()));
        }
    }

    private void addResponseBody(PaymentEvent event, Object result, AuditPayment auditPayment) {
        if (properties.isLogResponseBody() && result != null) {
            String body = auditPayloadProcessor.process(result, auditPayment.excludedFields());
            event.setResponseBody(body);
        }
    }

    private void markFailed(PaymentEvent event, Throwable e, long startTime) {
        event.setStatus(PaymentEvent.Status.FAILED);
        event.setError(e.getMessage());
        event.setDuration(System.currentTimeMillis() - startTime);
    }

    private void markSuccessful(PaymentEvent event, long startTime) {
        event.setStatus(PaymentEvent.Status.SUCCESS);
        event.setDuration(System.currentTimeMillis() - startTime);
    }

    private void publishAuditEvent(PaymentEvent event) {
        publisher.publish(event);
    }

    private void handleBusinessFailure(AuditPayment auditPayment, Throwable businessException, PaymentEvent event,
                                       long startTime) {
        markFailed(event, businessException, startTime);
        try {
            publishAuditEvent(event);
        } catch (RuntimeException auditException) {
            businessException.addSuppressed(auditException);
            LOGGER.error("Failed to publish audit event for failed business operation, correlationId={}",
                    event.getCorrelationId(), auditException);
        }
        recordMetricSafely(auditPayment.operation(), "error", event.getDuration());
    }

    private void recordMetricSafely(PaymentOperation operation, String status, long duration) {
        try {
            recordMetric(operation, status, duration);
        } catch (RuntimeException metricException) {
            LOGGER.error("Failed to record payment audit metric, operation={}, status={}", operation, status, metricException);
        }
    }

    private void recordMetric(PaymentOperation operation, String status, long duration) {
        meterRegistry.timer("payment.audit.duration",
                "operation", operation.name(),
                "status", status
        ).record(duration, TimeUnit.MILLISECONDS);
    }

}