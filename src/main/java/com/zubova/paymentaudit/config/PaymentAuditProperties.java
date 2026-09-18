package com.zubova.paymentaudit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.audit.payment")
public class PaymentAuditProperties {

    private boolean enabled = true;

    private boolean logRequestBody = true;
    private boolean logResponseBody = true;

    private boolean maskSensitiveData = true;
    private List<String> sensitiveFields = new ArrayList<>();

    private long maxBodySize = 10_000;

    private EventPublisherType eventPublisherType = EventPublisherType.KAFKA;

    private AuditFailureStrategy failureStrategy = AuditFailureStrategy.FAIL_OPEN;

    public List<String> getSensitiveFields() {
        return sensitiveFields;
    }

    public void setSensitiveFields(List<String> sensitiveFields) {
        this.sensitiveFields = sensitiveFields;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLogRequestBody() {
        return logRequestBody;
    }

    public void setLogRequestBody(boolean logRequestBody) {
        this.logRequestBody = logRequestBody;
    }

    public boolean isLogResponseBody() {
        return logResponseBody;
    }

    public void setLogResponseBody(boolean logResponseBody) {
        this.logResponseBody = logResponseBody;
    }

    public boolean isMaskSensitiveData() {
        return maskSensitiveData;
    }

    public void setMaskSensitiveData(boolean maskSensitiveData) {
        this.maskSensitiveData = maskSensitiveData;
    }

    public long getMaxBodySize() {
        return maxBodySize;
    }

    public void setMaxBodySize(long maxBodySize) {
        this.maxBodySize = maxBodySize;
    }

    public EventPublisherType getEventPublisherType() {
        return eventPublisherType;
    }

    public void setEventPublisherType(EventPublisherType eventPublisherType) {
        this.eventPublisherType = eventPublisherType;
    }

    public AuditFailureStrategy getFailureStrategy() {
        return failureStrategy;
    }

    public void setFailureStrategy(AuditFailureStrategy failureStrategy) {
        this.failureStrategy = failureStrategy;
    }

    public enum EventPublisherType {
        KAFKA
    }

}