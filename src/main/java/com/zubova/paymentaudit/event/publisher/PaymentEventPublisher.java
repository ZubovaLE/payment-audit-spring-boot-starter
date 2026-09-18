package com.zubova.paymentaudit.event.publisher;

import com.zubova.paymentaudit.event.PaymentEvent;

public interface PaymentEventPublisher {

    void publish(PaymentEvent event);

}