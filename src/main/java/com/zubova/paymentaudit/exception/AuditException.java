package com.zubova.paymentaudit.exception;

public class AuditException extends RuntimeException {

    public AuditException(String message, Throwable cause) {
        super(message, cause);
    }

}