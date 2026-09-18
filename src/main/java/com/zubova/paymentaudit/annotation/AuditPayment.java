package com.zubova.paymentaudit.annotation;

import com.zubova.paymentaudit.model.PaymentOperation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditPayment {

    PaymentOperation operation() default PaymentOperation.TRANSFER;

    String[] excludedFields() default {};

}